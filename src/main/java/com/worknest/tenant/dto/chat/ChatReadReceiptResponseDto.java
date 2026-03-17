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
public class ChatReadReceiptResponseDto {
    private Long id;
    private ChatType chatType;
    private Long messageId;
    private EmployeeSimpleDto employee;
    private LocalDateTime readAt;
}
