package com.worknest.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthCookieService {
    void issueAuthCookies(HttpServletResponse response, String refreshToken, String csrfToken, long maxAgeSeconds);

    void clearAuthCookies(HttpServletResponse response);

    String resolveRefreshToken(HttpServletRequest request, String fallbackToken);

    String resolveCsrfToken(HttpServletRequest request);

    void validateCsrfToken(HttpServletRequest request);
}