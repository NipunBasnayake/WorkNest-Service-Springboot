package com.worknest.auth.service;

public interface AuthLoginThrottleService {
    void assertLoginAllowed(String identifier, String ipAddress);

    void recordSuccessfulLogin(String identifier, String ipAddress);

    void recordFailedLogin(String identifier, String ipAddress, String userAgent);
}