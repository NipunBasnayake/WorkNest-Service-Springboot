package com.worknest.tenant.dto.employee;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeAccountProvisionRequestDto {

    @Size(min = 8, max = 100, message = "Temporary password must be between 8 and 100 characters")
    private String temporaryPassword;
}
