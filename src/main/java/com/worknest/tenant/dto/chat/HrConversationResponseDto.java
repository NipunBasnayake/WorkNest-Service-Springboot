package com.worknest.tenant.dto.chat;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class HrConversationResponseDto {
    private Long id;
    private EmployeeSimpleDto employee;
    private EmployeeSimpleDto hr;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
