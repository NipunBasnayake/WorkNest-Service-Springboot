package com.worknest.tenant.dto.attendance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceCheckOutRequestDto {

    @NotNull(message = "Employee ID is required")
    @Positive(message = "Employee ID must be positive")
    private Long employeeId;

    private boolean manualEntry;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String note;
}
