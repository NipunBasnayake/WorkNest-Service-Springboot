package com.worknest.tenant.service.impl;

import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.chat.ChatReadReceiptResponseDto;
import com.worknest.tenant.entity.ChatReadReceipt;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.HrMessage;
import com.worknest.tenant.entity.TeamChatMessage;
import com.worknest.tenant.enums.ChatType;
import com.worknest.tenant.repository.*;
import com.worknest.tenant.service.ChatReadReceiptService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(transactionManager = "transactionManager")
public class ChatReadReceiptServiceImpl implements ChatReadReceiptService {

    private final ChatReadReceiptRepository chatReadReceiptRepository;
    private final EmployeeRepository employeeRepository;
    private final HrMessageRepository hrMessageRepository;
    private final TeamChatMessageRepository teamChatMessageRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SecurityUtils securityUtils;
    private final TenantDtoMapper tenantDtoMapper;

    public ChatReadReceiptServiceImpl(
            ChatReadReceiptRepository chatReadReceiptRepository,
            EmployeeRepository employeeRepository,
            HrMessageRepository hrMessageRepository,
            TeamChatMessageRepository teamChatMessageRepository,
            TeamMemberRepository teamMemberRepository,
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper) {
        this.chatReadReceiptRepository = chatReadReceiptRepository;
        this.employeeRepository = employeeRepository;
        this.hrMessageRepository = hrMessageRepository;
        this.teamChatMessageRepository = teamChatMessageRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
    }

    @Override
    public ChatReadReceiptResponseDto markAsRead(ChatType chatType, Long messageId) {
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        validateMessageAccess(chatType, messageId, currentEmployee, true);

        ChatReadReceipt receipt = chatReadReceiptRepository
                .findByChatTypeAndMessageIdAndEmployeeId(chatType, messageId, currentEmployee.getId())
                .orElseGet(() -> {
                    ChatReadReceipt created = new ChatReadReceipt();
                    created.setChatType(chatType);
                    created.setMessageId(messageId);
                    created.setEmployee(currentEmployee);
                    return created;
                });

        receipt.setReadAt(LocalDateTime.now());
        ChatReadReceipt saved = chatReadReceiptRepository.save(receipt);
        return toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<ChatReadReceiptResponseDto> listReceipts(ChatType chatType, Long messageId) {
        Employee currentEmployee = getCurrentEmployeeOrThrow();
        validateMessageAccess(chatType, messageId, currentEmployee, false);

        return chatReadReceiptRepository.findByChatTypeAndMessageIdOrderByReadAtAsc(chatType, messageId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateMessageAccess(
            ChatType chatType,
            Long messageId,
            Employee currentEmployee,
            boolean markRead) {
        switch (chatType) {
            case HR -> {
                HrMessage hrMessage = hrMessageRepository.findById(messageId)
                        .orElseThrow(() -> new ResourceNotFoundException("HR message not found with id: " + messageId));

                boolean participant = hrMessage.getConversation().getEmployee().getId().equals(currentEmployee.getId())
                        || hrMessage.getConversation().getHr().getId().equals(currentEmployee.getId());
                if (!participant) {
                    throw new ForbiddenOperationException("You are not a participant of this HR conversation");
                }

                if (markRead && !hrMessage.getSender().getId().equals(currentEmployee.getId())) {
                    hrMessage.setRead(true);
                    hrMessageRepository.save(hrMessage);
                }
            }
            case TEAM -> {
                TeamChatMessage teamMessage = teamChatMessageRepository.findById(messageId)
                        .orElseThrow(() -> new ResourceNotFoundException("Team chat message not found with id: " + messageId));

                Long teamId = teamMessage.getTeamChat().getTeam().getId();
                boolean isMember = teamMemberRepository
                        .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, currentEmployee.getId())
                        .isPresent();
                boolean isManager = teamMessage.getTeamChat().getTeam().getManager() != null
                        && teamMessage.getTeamChat().getTeam().getManager().getId().equals(currentEmployee.getId());
                if (!isMember && !isManager) {
                    throw new ForbiddenOperationException("You are not allowed to access this team chat message");
                }
            }
        }
    }

    private Employee getCurrentEmployeeOrThrow() {
        String email = securityUtils.getCurrentUserEmailOrThrow();
        return employeeRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Current user does not have an employee profile"));
    }

    private ChatReadReceiptResponseDto toResponse(ChatReadReceipt readReceipt) {
        return ChatReadReceiptResponseDto.builder()
                .id(readReceipt.getId())
                .chatType(readReceipt.getChatType())
                .messageId(readReceipt.getMessageId())
                .employee(tenantDtoMapper.toEmployeeSimple(readReceipt.getEmployee()))
                .readAt(readReceipt.getReadAt())
                .build();
    }
}
