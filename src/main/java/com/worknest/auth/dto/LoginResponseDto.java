package com.worknest.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {
    private String tokenType;
    private String accessToken;
    private LocalDateTime accessTokenExpiresAt;
    private String refreshToken;
    private LocalDateTime refreshTokenExpiresAt;
    private boolean passwordChangeRequired;
    private AuthUserDto user;
}
