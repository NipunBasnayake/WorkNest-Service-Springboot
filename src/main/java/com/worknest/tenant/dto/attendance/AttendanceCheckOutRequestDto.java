package com.worknest.tenant.dto.attendance;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceCheckOutRequestDto {

    @NotNull(message = "Employee ID is required")
    private Long employeeId;
}
