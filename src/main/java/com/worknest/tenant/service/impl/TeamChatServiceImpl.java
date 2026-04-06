package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.util.SecurityUtils;
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
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.TeamChatMessageRepository;
import com.worknest.tenant.repository.TeamChatRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import com.worknest.tenant.service.TeamChatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "transactionManager")
public class TeamChatServiceImpl implements TeamChatService {

    private final TeamChatRepository teamChatRepository;
    private final TeamChatMessageRepository teamChatMessageRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EmployeeRepository employeeRepository;
    private final SecurityUtils securityUtils;
    private final TenantDtoMapper tenantDtoMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final TenantRealtimePublisher tenantRealtimePublisher;

    public TeamChatServiceImpl(
            TeamChatRepository teamChatRepository,
            TeamChatMessageRepository teamChatMessageRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            EmployeeRepository employeeRepository,
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            TenantRealtimePublisher tenantRealtimePublisher) {
        this.teamChatRepository = teamChatRepository;
        this.teamChatMessageRepository = teamChatMessageRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.employeeRepository = employeeRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.tenantRealtimePublisher = tenantRealtimePublisher;
    }

    @Override
    public TeamChatResponseDto createOrGetTeamChat(TeamChatCreateRequestDto requestDto) {
        return getOrCreateByTeam(requestDto.getTeamId());
    }

    @Override
    public TeamChatResponseDto getOrCreateByTeam(Long teamId) {
        Team team = getTeamOrThrow(teamId);
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        ensureTeamChatAccess(team, currentEmployee);

        TeamChat teamChat = teamChatRepository.findByTeamId(teamId)
                .orElseGet(() -> {
                    TeamChat created = new TeamChat();
                    created.setTeam(team);
                    TeamChat saved = teamChatRepository.save(created);
                    auditLogService.logAction(
                            AuditActionType.CREATE,
                            AuditEntityType.TEAM_CHAT,
                            saved.getId(),
                            "{\"teamId\":" + teamId + "}"
                    );
                    return saved;
                });

        return toTeamChatResponse(teamChat);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TeamChatResponseDto> listMyTeamChats() {
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

        return teamChatRepository.findByTeamIdInOrderByUpdatedAtDesc(teamIds).stream()
                .map(this::toTeamChatResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TeamChatMessageResponseDto> listMessages(Long teamChatId) {
        TeamChat teamChat = getTeamChatOrThrow(teamChatId);
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        ensureTeamChatAccess(teamChat.getTeam(), currentEmployee);

        return teamChatMessageRepository.findByTeamChatIdOrderByCreatedAtAsc(teamChatId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Override
    public TeamChatMessageResponseDto sendMessage(Long teamChatId, TeamChatMessageSendRequestDto requestDto) {
        TeamChat teamChat = getTeamChatOrThrow(teamChatId);
        Team team = teamChat.getTeam();

        Employee currentEmployee = getCurrentEmployeeOrThrow();
        Employee sender = getEmployeeOrThrow(requestDto.getSenderEmployeeId());

        if (!currentEmployee.getId().equals(sender.getId())) {
            throw new ForbiddenOperationException("Sender must match the authenticated employee");
        }

        ensureTeamChatAccess(team, currentEmployee);

        TeamChatMessage teamChatMessage = new TeamChatMessage();
        teamChatMessage.setTeamChat(teamChat);
        teamChatMessage.setSender(sender);
        teamChatMessage.setMessage(requestDto.getMessage().trim());

        teamChat.setUpdatedAt(LocalDateTime.now());
        teamChatRepository.save(teamChat);

        TeamChatMessage saved = teamChatMessageRepository.save(teamChatMessage);
        TeamChatMessageResponseDto response = toMessageResponse(saved);

        notifyTeamParticipants(team, sender.getId(), saved.getId());
        tenantRealtimePublisher.publishTeamMessage(
                securityUtils.getCurrentTenantKeyOrThrow(),
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

    private Employee getCurrentEmployeeOrThrow() {
        String email = securityUtils.getCurrentUserEmailOrThrow();
        return employeeRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Current user does not have an employee profile"));
    }

    private Employee getEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
    }

    private void ensureTeamChatAccess(Team team, Employee currentEmployee) {
        if (isPrivilegedRole()) {
            return;
        }
        if (!isTeamParticipant(team, currentEmployee.getId())) {
            throw new ForbiddenOperationException("Only team participants can access team chat");
        }
    }

    private boolean isPrivilegedRole() {
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        return role == PlatformRole.TENANT_ADMIN || role == PlatformRole.ADMIN;
    }

    private boolean isTeamParticipant(Team team, Long employeeId) {
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
        return TeamChatResponseDto.builder()
                .id(teamChat.getId())
                .teamId(teamChat.getTeam().getId())
                .teamName(teamChat.getTeam().getName())
                .createdAt(teamChat.getCreatedAt())
                .updatedAt(teamChat.getUpdatedAt())
                .build();
    }

    private TeamChatMessageResponseDto toMessageResponse(TeamChatMessage teamChatMessage) {
        return TeamChatMessageResponseDto.builder()
                .id(teamChatMessage.getId())
                .teamChatId(teamChatMessage.getTeamChat().getId())
                .sender(tenantDtoMapper.toEmployeeSimple(teamChatMessage.getSender()))
                .message(teamChatMessage.getMessage())
                .createdAt(teamChatMessage.getCreatedAt())
                .build();
    }
}
