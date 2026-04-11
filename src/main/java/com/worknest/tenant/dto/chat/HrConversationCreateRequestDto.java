package com.worknest.tenant.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HrConversationCreateRequestDto {

    @NotNull(message = "Employee ID is required")
    @Positive(message = "Employee ID must be positive")
    private Long employeeId;

    @NotNull(message = "HR employee ID is required")
    @Positive(message = "HR employee ID must be positive")
    private Long hrId;
}
