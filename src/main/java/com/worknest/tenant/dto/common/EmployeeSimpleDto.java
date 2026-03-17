package com.worknest.tenant.dto.common;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class EmployeeSimpleDto {
    private Long id;
    private String employeeCode;
    private String fullName;
    private String email;
    private PlatformRole role;
    private UserStatus status;
}
