package com.worknest.tenant.dto.employee;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class EmployeeAccountProvisionResponseDto {
    private Long employeeId;
    private Long platformUserId;
    private String email;
    private String tenantKey;
    private PlatformRole role;
    private UserStatus status;
    private boolean accountProvisioned;
    private String temporaryPassword;
}
