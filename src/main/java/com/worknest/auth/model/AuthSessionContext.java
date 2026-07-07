package com.worknest.auth.model;

public record AuthSessionContext(
        String deviceId,
        String deviceName,
        String userAgent,
        String ipAddress,
        boolean suspicious,
        String suspiciousReason) {
}