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
public class AuthSessionDto {
    private Long id;
    private String deviceId;
    private String deviceName;
    private String userAgent;
    private String ipAddress;
    private boolean suspicious;
    private String suspiciousReason;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
    private boolean revoked;
    private boolean currentSession;
}