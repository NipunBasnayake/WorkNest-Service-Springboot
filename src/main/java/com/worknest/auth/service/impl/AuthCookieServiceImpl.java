package com.worknest.auth.service.impl;

import com.worknest.auth.service.AuthCookieService;
import com.worknest.common.exception.ForbiddenOperationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

@Service
public class AuthCookieServiceImpl implements AuthCookieService {

    private static final String REFRESH_COOKIE_NAME = "wn_refresh_token";
    private static final String CSRF_COOKIE_NAME = "wn_csrf_token";
    private static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";
    private static final String CSRF_ALT_HEADER_NAME = "X-XSRF-TOKEN";

    private final boolean secureCookies;
    private final String sameSite;
    private final String cookiePath;

    public AuthCookieServiceImpl(
            @Value("${app.auth.cookies.secure:true}") boolean secureCookies,
            @Value("${app.auth.cookies.same-site:Lax}") String sameSite,
            @Value("${app.auth.cookies.path:/}") String cookiePath) {
        this.secureCookies = secureCookies;
        this.sameSite = sameSite;
        this.cookiePath = cookiePath;
    }

    @Override
    public void issueAuthCookies(HttpServletResponse response, String refreshToken, String csrfToken, long maxAgeSeconds) {
        response.addHeader("Set-Cookie", buildCookieHeader(REFRESH_COOKIE_NAME, refreshToken, maxAgeSeconds, true));
        response.addHeader("Set-Cookie", buildCookieHeader(CSRF_COOKIE_NAME, csrfToken, maxAgeSeconds, false));
    }

    @Override
    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookieHeader(REFRESH_COOKIE_NAME, "", 0, true));
        response.addHeader("Set-Cookie", buildCookieHeader(CSRF_COOKIE_NAME, "", 0, false));
    }

    @Override
    public String resolveRefreshToken(HttpServletRequest request, String fallbackToken) {
        String cookieToken = resolveCookieValue(request, REFRESH_COOKIE_NAME).orElse(null);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }
        return fallbackToken;
    }

    @Override
    public String resolveCsrfToken(HttpServletRequest request) {
        return resolveCookieValue(request, CSRF_COOKIE_NAME).orElse(null);
    }

    @Override
    public void validateCsrfToken(HttpServletRequest request) {
        String csrfCookie = resolveCsrfToken(request);
        if (csrfCookie == null || csrfCookie.isBlank()) {
            throw new ForbiddenOperationException("CSRF token cookie is missing");
        }

        String csrfHeader = Optional.ofNullable(request.getHeader(CSRF_HEADER_NAME))
                .filter(value -> !value.isBlank())
                .orElse(request.getHeader(CSRF_ALT_HEADER_NAME));

        if (csrfHeader == null || csrfHeader.isBlank() || !csrfCookie.equals(csrfHeader.trim())) {
            throw new ForbiddenOperationException("CSRF token validation failed");
        }
    }

    private String buildCookieHeader(String name, String value, long maxAgeSeconds, boolean httpOnly) {
        StringBuilder builder = new StringBuilder();
        builder.append(name).append('=').append(value == null ? "" : value)
                .append("; Path=").append(cookiePath)
                .append("; Max-Age=").append(Math.max(maxAgeSeconds, 0));

        if (httpOnly) {
            builder.append("; HttpOnly");
        }
        if (secureCookies) {
            builder.append("; Secure");
        }
        if (sameSite != null && !sameSite.isBlank()) {
            builder.append("; SameSite=").append(sameSite);
        }
        return builder.toString();
    }

    private Optional<String> resolveCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}