package com.worknest.tenant.dto.chat;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TeamChatMessageResponseDto {
    private Long id;
    private Long teamChatId;
    private EmployeeSimpleDto sender;
    private String message;
    private LocalDateTime createdAt;
}
