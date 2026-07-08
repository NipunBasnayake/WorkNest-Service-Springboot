package com.worknest.tenant.dto.chat;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.ChatType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TeamChatMessageResponseDto {
    private Long id;
    private ChatType chatType;
    private Long conversationId;
    private Long teamChatId;
    private EmployeeSimpleDto sender;
    private Long senderEmployeeId;
    private String senderName;
    private String message;
    private LocalDateTime createdAt;
}
