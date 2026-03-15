package com.worknest.tenant.dto.employee;

import com.worknest.common.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeStatusUpdateDto {

    @NotNull(message = "Status is required")
    private UserStatus status;
}
