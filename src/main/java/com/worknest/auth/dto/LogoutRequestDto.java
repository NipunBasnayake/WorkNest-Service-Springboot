package com.worknest.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LogoutRequestDto {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    @Size(max = 50, message = "Tenant key must not exceed 50 characters")
    private String tenantKey;
}
