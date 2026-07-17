package com.worknest.tenant.service.impl;

import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.storage.FileStorageService;
import com.worknest.common.storage.StorageCategory;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.dto.chat.TeamChatCreateRequestDto;
import com.worknest.tenant.dto.chat.TeamChatMessageResponseDto;
import com.worknest.tenant.dto.chat.TeamChatMessageSendRequestDto;
import com.worknest.tenant.dto.chat.TeamChatResponseDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.entity.TeamChat;
import com.worknest.tenant.entity.TeamChatMessage;
import com.worknest.tenant.entity.TeamMember;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.ChatType;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.TeamChatMessageRepository;
import com.worknest.tenant.repository.TeamChatRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import com.worknest.tenant.service.TeamChatService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "transactionManager")
public class TeamChatServiceImpl implements TeamChatService {

    private final TeamChatRepository teamChatRepository;
    private final TeamChatMessageRepository teamChatMessageRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuthorizationService authorizationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TenantRealtimePublisher tenantRealtimePublisher;
    private final FileStorageService fileStorageService;

    public TeamChatServiceImpl(
            TeamChatRepository teamChatRepository,
            TeamChatMessageRepository teamChatMessageRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            AuthorizationService authorizationService,
            TenantDtoMapper tenantDtoMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TenantRealtimePublisher tenantRealtimePublisher,
            FileStorageService fileStorageService) {
        this.teamChatRepository = teamChatRepository;
        this.teamChatMessageRepository = teamChatMessageRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.authorizationService = authorizationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.tenantRealtimePublisher = tenantRealtimePublisher;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public TeamChatResponseDto createOrGetTeamChat(TeamChatCreateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        return getOrCreateByTeam(requestDto.getTeamId());
    }

    @Override
    public TeamChatResponseDto getOrCreateByTeam(Long teamId) {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        Team team = getTeamOrThrow(teamId);
        Employee currentEmployee = authorizationService.getCurrentEmployeeOrNull();
        ensureTeamChatAccess(team, currentEmployee);

        TeamChat teamChat = getOrCreateTeamChat(team);

        return toTeamChatResponse(teamChat);
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public List<TeamChatResponseDto> listMyTeamChats() {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        Employee currentEmployee = getCurrentEmployeeOrThrow();

        Set<Long> teamIds = teamMemberRepository.findByEmployeeIdAndLeftAtIsNull(currentEmployee.getId())
                .stream()
                .map(teamMember -> teamMember.getTeam().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        teamRepository.findByManagerId(currentEmployee.getId())
                .stream()
                .map(Team::getId)
                .forEach(teamIds::add);

        if (teamIds.isEmpty()) {
            return List.of();
        }

        return teamRepository.findAllById(teamIds).stream()
                .map(this::getOrCreateTeamChat)
                .sorted(java.util.Comparator.comparing(TeamChat::getUpdatedAt).reversed())
                .map(this::toTeamChatResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TeamChatMessageResponseDto> listMessages(Long teamChatId) {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        TeamChat teamChat = getTeamChatOrThrow(teamChatId);
        Employee currentEmployee = authorizationService.getCurrentEmployeeOrNull();
        ensureTeamChatAccess(teamChat.getTeam(), currentEmployee);

        return teamChatMessageRepository.findByTeamChatIdOrderByCreatedAtAsc(teamChatId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Override
    public TeamChatMessageResponseDto sendMessage(Long teamChatId, TeamChatMessageSendRequestDto requestDto) {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        TeamChat teamChat = getTeamChatOrThrow(teamChatId);
        Team team = teamChat.getTeam();

        Employee currentEmployee = getCurrentEmployeeOrThrow();
        Employee sender = currentEmployee;

        ensureTeamChatAccess(team, currentEmployee);

        TeamChatMessage teamChatMessage = new TeamChatMessage();
        teamChatMessage.setTeamChat(teamChat);
        teamChatMessage.setSender(sender);
        teamChatMessage.setMessage(requestDto.getMessage().trim());

        teamChat.setUpdatedAt(LocalDateTime.now());
        teamChatRepository.save(teamChat);

        TeamChatMessage saved = teamChatMessageRepository.save(teamChatMessage);
        linkAttachments(requestDto.getAttachmentReferences(), saved.getId());
        TeamChatMessageResponseDto response = toMessageResponse(saved);

        notifyTeamParticipants(team, sender.getId(), saved.getId());
        tenantRealtimePublisher.publishTeamMessage(
                authorizationService.getCurrentTenantKeyOrThrow(),
                teamChat.getId(),
                response
        );

        auditLogService.logAction(
                AuditActionType.SEND_MESSAGE,
                AuditEntityType.TEAM_CHAT_MESSAGE,
                saved.getId(),
                "{\"teamChatId\":" + teamChatId + "}"
        );

        return response;
    }

    private Team getTeamOrThrow(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));
    }

    private TeamChat getTeamChatOrThrow(Long teamChatId) {
        return teamChatRepository.findById(teamChatId)
                .orElseThrow(() -> new ResourceNotFoundException("Team chat not found with id: " + teamChatId));
    }

    private TeamChat getOrCreateTeamChat(Team team) {
        return teamChatRepository.findByTeamId(team.getId())
                .orElseGet(() -> {
                    try {
                        TeamChat created = new TeamChat();
                        created.setTeam(team);
                        TeamChat saved = teamChatRepository.save(created);
                        auditLogService.logAction(
                                AuditActionType.CREATE,
                                AuditEntityType.TEAM_CHAT,
                                saved.getId(),
                                "{\"teamId\":" + team.getId() + "}"
                        );
                        return saved;
                    } catch (DataIntegrityViolationException ex) {
                        return teamChatRepository.findByTeamId(team.getId()).orElseThrow(() -> ex);
                    }
                });
    }

    private Employee getCurrentEmployeeOrThrow() {
        return authorizationService.getCurrentEmployeeOrThrow();
    }

    private void ensureTeamChatAccess(Team team, Employee currentEmployee) {
        if (currentEmployee == null) {
            throw new ForbiddenOperationException("Only team participants can access team chat");
        }
        if (!isTeamParticipant(team, currentEmployee.getId())) {
            throw new ForbiddenOperationException("Only team participants can access team chat");
        }
    }

    private boolean isTeamParticipant(Team team, Long employeeId) {
        if (employeeId == null) {
            return false;
        }
        if (team.getManager() != null && employeeId.equals(team.getManager().getId())) {
            return true;
        }
        return teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(team.getId(), employeeId).isPresent();
    }

    private void notifyTeamParticipants(Team team, Long senderEmployeeId, Long messageId) {
        Set<Long> recipientIds = teamMemberRepository.findByTeamIdOrderByJoinedAtDesc(team.getId()).stream()
                .filter(member -> member.getLeftAt() == null)
                .map(TeamMember::getEmployee)
                .map(Employee::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (team.getManager() != null) {
            recipientIds.add(team.getManager().getId());
        }

        recipientIds.remove(senderEmployeeId);
        for (Long recipientId : recipientIds) {
            notificationService.createSystemNotification(
                    recipientId,
                    NotificationType.TEAM_MESSAGE,
                    "New team chat message in " + team.getName(),
                    AuditEntityType.TEAM_CHAT_MESSAGE.name(),
                    messageId
            );
        }
    }

    private TeamChatResponseDto toTeamChatResponse(TeamChat teamChat) {
        TeamChatMessage latestMessage = teamChatMessageRepository
                .findFirstByTeamChatIdOrderByCreatedAtDesc(teamChat.getId())
                .orElse(null);
        Long currentEmployeeId = authorizationService.getCurrentEmployeeIdOrNull();
        long unreadCount = currentEmployeeId == null
                ? 0L
                : teamChatMessageRepository.countUnreadForEmployee(teamChat.getId(), currentEmployeeId, ChatType.TEAM);

        return TeamChatResponseDto.builder()
                .id(teamChat.getId())
                .teamId(teamChat.getTeam().getId())
                .teamName(teamChat.getTeam().getName())
                .participants(teamParticipants(teamChat.getTeam()))
                .lastMessage(latestMessage == null ? null : latestMessage.getMessage())
                .lastMessageAt(latestMessage == null ? teamChat.getUpdatedAt() : latestMessage.getCreatedAt())
                .unreadCount(unreadCount)
                .createdAt(teamChat.getCreatedAt())
                .updatedAt(teamChat.getUpdatedAt())
                .build();
    }

    private TeamChatMessageResponseDto toMessageResponse(TeamChatMessage teamChatMessage) {
        Employee sender = teamChatMessage.getSender();
        return TeamChatMessageResponseDto.builder()
                .id(teamChatMessage.getId())
                .chatType(ChatType.TEAM)
                .conversationId(teamChatMessage.getTeamChat().getId())
                .teamChatId(teamChatMessage.getTeamChat().getId())
                .sender(tenantDtoMapper.toEmployeeSimple(sender))
                .senderEmployeeId(sender.getId())
                .senderName(buildFullName(sender))
                .message(teamChatMessage.getMessage())
                .createdAt(teamChatMessage.getCreatedAt())
                .attachments(fileStorageService.listLinkedFiles("TEAM_CHAT_MESSAGE", teamChatMessage.getId()))
                .build();
    }

    private void linkAttachments(List<String> references, Long messageId) {
        if (references == null) return;
        references.stream()
                .filter(java.util.Objects::nonNull)
                .map(fileStorageService::normalizeStoredReference)
                .distinct()
                .forEach(reference -> fileStorageService.claimAndLink(
                        reference,
                        "TEAM_CHAT_MESSAGE",
                        messageId,
                        StorageCategory.CHAT_ATTACHMENT));
    }

    private List<EmployeeSimpleDto> teamParticipants(Team team) {
        Map<Long, Employee> participants = new LinkedHashMap<>();
        if (team.getManager() != null) {
            participants.put(team.getManager().getId(), team.getManager());
        }

        teamMemberRepository.findByTeamIdAndLeftAtIsNull(team.getId()).stream()
                .map(TeamMember::getEmployee)
                .forEach(employee -> participants.putIfAbsent(employee.getId(), employee));

        return participants.values().stream()
                .map(tenantDtoMapper::toEmployeeSimple)
                .toList();
    }

    private String buildFullName(Employee employee) {
        if (employee == null) {
            return "-";
        }
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? employee.getEmail() : fullName;
    }
}
