package com.worknest.auth.dto;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserDto {
    private Long id;
    private String fullName;
    private String email;
    private PlatformRole role;
    private UserStatus status;
    private String tenantKey;
}
