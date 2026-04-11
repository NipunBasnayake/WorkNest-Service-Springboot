package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.chat.*;
import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.HrConversation;
import com.worknest.tenant.entity.HrMessage;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.ChatType;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.HrConversationRepository;
import com.worknest.tenant.repository.HrMessageRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.ChatReadReceiptService;
import com.worknest.tenant.service.HrChatService;
import com.worknest.tenant.service.NotificationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(transactionManager = "transactionManager")
public class HrChatServiceImpl implements HrChatService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9._-]{2,64})");

    private final HrConversationRepository hrConversationRepository;
    private final HrMessageRepository hrMessageRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final ChatReadReceiptService chatReadReceiptService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final TenantRealtimePublisher tenantRealtimePublisher;
    private final EmailNotificationService emailNotificationService;

    public HrChatServiceImpl(
            HrConversationRepository hrConversationRepository,
            HrMessageRepository hrMessageRepository,
            EmployeeRepository employeeRepository,
            AuthorizationService authorizationService,
            TenantDtoMapper tenantDtoMapper,
            ChatReadReceiptService chatReadReceiptService,
            AuditLogService auditLogService,
            NotificationService notificationService,
            TenantRealtimePublisher tenantRealtimePublisher,
            EmailNotificationService emailNotificationService) {
        this.hrConversationRepository = hrConversationRepository;
        this.hrMessageRepository = hrMessageRepository;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.chatReadReceiptService = chatReadReceiptService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.tenantRealtimePublisher = tenantRealtimePublisher;
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    public HrConversationResponseDto createOrGetConversation(HrConversationCreateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        if (requestDto.getEmployeeId().equals(requestDto.getHrId())) {
            throw new BadRequestException("Employee and HR participants must be different users");
        }

        Employee employee = getActiveEmployeeOrThrow(requestDto.getEmployeeId());
        Employee hr = getActiveEmployeeOrThrow(requestDto.getHrId());

        validateConversationRoles(employee, hr);
        validateCurrentUserCanAccessParticipant(requestDto.getEmployeeId(), requestDto.getHrId());

        HrConversation conversation;
        try {
            conversation = hrConversationRepository
                    .findByEmployeeIdAndHrId(employee.getId(), hr.getId())
                    .orElseGet(() -> {
                        HrConversation created = new HrConversation();
                        created.setEmployee(employee);
                        created.setHr(hr);
                        HrConversation saved = hrConversationRepository.save(created);
                        auditLogService.logAction(
                                AuditActionType.CREATE,
                                AuditEntityType.HR_MESSAGE,
                                saved.getId(),
                                "{\"employeeId\":" + employee.getId() + ",\"hrId\":" + hr.getId() + "}"
                        );
                        return saved;
                    });
        } catch (DataIntegrityViolationException ex) {
            // Handle concurrent create requests that race on the unique employee/hr constraint.
            conversation = hrConversationRepository.findByEmployeeIdAndHrId(employee.getId(), hr.getId())
                    .orElseThrow(() -> ex);
        }

        return toConversationResponse(conversation);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public HrConversationTargetsResponseDto listConversationTargets() {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        Employee me = authorizationService.getCurrentEmployeeOrNull();
        List<Employee> activeEmployees = employeeRepository.findByStatus(UserStatus.ACTIVE);

        List<EmployeeSimpleDto> hrTargets = activeEmployees.stream()
                .filter(candidate -> me == null || !candidate.getId().equals(me.getId()))
                .filter(candidate -> isHrRole(candidate.getRole()))
                .sorted(Comparator.comparing(this::toComparableName))
                .map(tenantDtoMapper::toEmployeeSimple)
                .toList();

        List<EmployeeSimpleDto> employeeTargets = activeEmployees.stream()
                .filter(candidate -> me == null || !candidate.getId().equals(me.getId()))
                .filter(candidate -> isEmployeeParticipantRole(candidate.getRole()))
                .sorted(Comparator.comparing(this::toComparableName))
                .map(tenantDtoMapper::toEmployeeSimple)
                .toList();

        return HrConversationTargetsResponseDto.builder()
                .hrTargets(hrTargets)
                .employeeTargets(employeeTargets)
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<HrConversationResponseDto> listMyConversations() {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        if (authorizationService.getCurrentRoleOrThrow().isTenantAdminEquivalent()) {
            return hrConversationRepository.findAll().stream()
                    .sorted(Comparator.comparing(HrConversation::getUpdatedAt).reversed())
                    .map(this::toConversationResponse)
                    .toList();
        }
        Employee me = getCurrentEmployeeOrThrow();
        return hrConversationRepository.findByEmployeeIdOrHrIdOrderByUpdatedAtDesc(me.getId(), me.getId())
                .stream()
                .map(this::toConversationResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<HrMessageResponseDto> listMessages(Long conversationId) {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        HrConversation conversation = getConversationOrThrow(conversationId);
        Employee me = authorizationService.getCurrentEmployeeOrNull();
        ensureConversationAccess(conversation, me);

        return hrMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Override
    public HrMessageResponseDto sendMessage(Long conversationId, HrMessageSendRequestDto requestDto) {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        HrConversation conversation = getConversationOrThrow(conversationId);
        Employee me = getCurrentEmployeeOrThrow();
        Employee sender = me;

        ensureConversationAccess(conversation, me);
        boolean senderIsParticipant = conversation.getEmployee().getId().equals(sender.getId())
                || conversation.getHr().getId().equals(sender.getId());
        if (!senderIsParticipant) {
            throw new ForbiddenOperationException("Sender is not a participant in this conversation");
        }

        HrMessage message = new HrMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setMessage(requestDto.getMessage().trim());
        message.setRead(false);

        conversation.setUpdatedAt(LocalDateTime.now());
        hrConversationRepository.save(conversation);

        HrMessage saved = hrMessageRepository.save(message);
        HrMessageResponseDto response = toMessageResponse(saved);

        Employee recipient = conversation.getEmployee().getId().equals(sender.getId())
                ? conversation.getHr()
                : conversation.getEmployee();

        notificationService.createSystemNotification(
                recipient.getId(),
                NotificationType.HR_MESSAGE,
                "New HR chat message",
                AuditEntityType.HR_MESSAGE.name(),
                saved.getId()
        );
        notifyMentionedParticipants(conversation, sender, saved.getMessage(), saved.getId());

        tenantRealtimePublisher.publishHrMessage(
                authorizationService.getCurrentTenantKeyOrThrow(),
                conversationId,
                response
        );

        auditLogService.logAction(
                AuditActionType.SEND_MESSAGE,
                AuditEntityType.HR_MESSAGE,
                saved.getId(),
                "{\"conversationId\":" + conversationId + "}"
        );

        return response;
    }

    @Override
    public long markConversationMessagesAsRead(Long conversationId) {
        authorizationService.requirePermission(Permission.CHAT_ACCESS);
        HrConversation conversation = getConversationOrThrow(conversationId);
        Employee me = authorizationService.getCurrentEmployeeOrNull();
        ensureConversationAccess(conversation, me);

        if (me == null) {
            return 0L;
        }

        List<HrMessage> unreadMessages = hrMessageRepository
                .findByConversationIdAndSenderIdNotAndReadFalse(conversationId, me.getId());

        for (HrMessage message : unreadMessages) {
            message.setRead(true);
            chatReadReceiptService.markAsRead(ChatType.HR, message.getId());
        }

        hrMessageRepository.saveAll(unreadMessages);
        return unreadMessages.size();
    }

    private HrConversation getConversationOrThrow(Long conversationId) {
        return hrConversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("HR conversation not found with id: " + conversationId));
    }

    private Employee getEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
    }

    private Employee getActiveEmployeeOrThrow(Long employeeId) {
        Employee employee = getEmployeeOrThrow(employeeId);
        if (employee.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Only active employees can participate in HR conversations");
        }
        return employee;
    }

    private Employee getCurrentEmployeeOrThrow() {
        return authorizationService.getCurrentEmployeeOrThrow();
    }

    private void ensureConversationAccess(HrConversation conversation, Employee employee) {
        if (authorizationService.getCurrentRoleOrThrow().isTenantAdminEquivalent()) {
            return;
        }
        if (employee == null) {
            throw new ForbiddenOperationException("You are not allowed to access this HR conversation");
        }
        boolean allowed = conversation.getEmployee().getId().equals(employee.getId())
                || conversation.getHr().getId().equals(employee.getId());
        if (!allowed) {
            throw new ForbiddenOperationException("You are not allowed to access this HR conversation");
        }
    }

    private void validateConversationRoles(Employee employee, Employee hr) {
        if (isHrRole(employee.getRole())) {
            throw new BadRequestException("Employee participant should not be an HR/Admin role");
        }

        if (!isHrRole(hr.getRole())) {
            throw new BadRequestException("HR participant must have HR or ADMIN role");
        }
    }

    private void validateCurrentUserCanAccessParticipant(Long employeeId, Long hrId) {
        if (authorizationService.getCurrentRoleOrThrow().isTenantAdminEquivalent()) {
            return;
        }
        Employee me = getCurrentEmployeeOrThrow();
        if (!me.getId().equals(employeeId) && !me.getId().equals(hrId)) {
            throw new ForbiddenOperationException("You can only open conversations where you are a participant");
        }
    }

    private boolean isHrRole(PlatformRole role) {
        return role != null && (role.isHrEquivalent() || role.isTenantAdminEquivalent());
    }

    private boolean isEmployeeParticipantRole(PlatformRole role) {
        return role != null && !isHrRole(role);
    }

    private String toComparableName(Employee employee) {
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        return (firstName + " " + lastName).trim().toLowerCase();
    }

    private HrConversationResponseDto toConversationResponse(HrConversation conversation) {
        return HrConversationResponseDto.builder()
                .id(conversation.getId())
                .employee(tenantDtoMapper.toEmployeeSimple(conversation.getEmployee()))
                .hr(tenantDtoMapper.toEmployeeSimple(conversation.getHr()))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private HrMessageResponseDto toMessageResponse(HrMessage message) {
        return HrMessageResponseDto.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .sender(tenantDtoMapper.toEmployeeSimple(message.getSender()))
                .message(message.getMessage())
                .read(message.isRead())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private void notifyMentionedParticipants(
            HrConversation conversation,
            Employee sender,
            String message,
            Long messageId) {
        if (message == null || message.isBlank()) {
            return;
        }

        Set<String> mentions = extractMentions(message);
        if (mentions.isEmpty()) {
            return;
        }

        Set<Employee> candidateRecipients = new LinkedHashSet<>();
        if (!conversation.getEmployee().getId().equals(sender.getId())) {
            candidateRecipients.add(conversation.getEmployee());
        }
        if (!conversation.getHr().getId().equals(sender.getId())) {
            candidateRecipients.add(conversation.getHr());
        }

        for (Employee recipient : candidateRecipients) {
            if (!matchesMention(recipient, mentions)) {
                continue;
            }
            notificationService.createSystemNotification(
                    recipient.getId(),
                    NotificationType.HR_MESSAGE,
                    "You were mentioned in HR chat",
                    AuditEntityType.HR_MESSAGE.name(),
                    messageId
            );
            emailNotificationService.sendHrMentionEmail(
                    recipient.getEmail(),
                    buildFullName(recipient),
                    buildFullName(sender),
                    truncateMessage(message, 300)
            );
        }
    }

    private Set<String> extractMentions(String message) {
        Set<String> mentions = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(message);
        while (matcher.find()) {
            mentions.add(matcher.group(1).toLowerCase());
        }
        return mentions;
    }

    private boolean matchesMention(Employee employee, Set<String> mentions) {
        Set<String> aliases = new LinkedHashSet<>();
        if (employee.getEmail() != null) {
            String email = employee.getEmail().trim().toLowerCase();
            aliases.add(email);
            int atIndex = email.indexOf('@');
            if (atIndex > 0) {
                aliases.add(email.substring(0, atIndex));
            }
        }
        if (employee.getEmployeeCode() != null) {
            aliases.add(employee.getEmployeeCode().trim().toLowerCase());
        }
        if (employee.getFirstName() != null) {
            aliases.add(employee.getFirstName().trim().toLowerCase());
        }
        if (employee.getLastName() != null) {
            aliases.add(employee.getLastName().trim().toLowerCase());
        }

        for (String mention : mentions) {
            if (aliases.contains(mention)) {
                return true;
            }
        }
        return false;
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

    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "";
        }
        String normalized = message.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }
}
