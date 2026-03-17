package com.worknest.auth.service.impl;

import com.worknest.auth.dto.*;
import com.worknest.auth.service.AuthService;
import com.worknest.auth.service.PlatformUserService;
import com.worknest.auth.service.RefreshTokenService;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.InactiveUserException;
import com.worknest.common.exception.InvalidCredentialsException;
import com.worknest.common.exception.TenantNotFoundException;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.RefreshToken;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.security.jwt.JwtService;
import com.worknest.security.model.PlatformUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class AuthServiceImpl implements AuthService {

    private final PlatformUserService platformUserService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MasterTenantLookupService masterTenantLookupService;
    private final String tenantHeaderName;

    public AuthServiceImpl(
            PlatformUserService platformUserService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            MasterTenantLookupService masterTenantLookupService,
            @Value("${app.tenant.header:X-Tenant-ID}") String tenantHeaderName) {
        this.platformUserService = platformUserService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.masterTenantLookupService = masterTenantLookupService;
        this.tenantHeaderName = tenantHeaderName;
    }

    @Override
    public LoginResponseDto login(LoginRequestDto requestDto) {
        PlatformUser user = platformUserService.findByEmailOrThrow(requestDto.getEmail());

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InactiveUserException("User account is inactive");
        }

        validateUserTenantScope(user, requestDto.getTenantKey(), false);

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        refreshTokenService.revokeAllActiveTokens(user);
        RefreshToken refreshToken = refreshTokenService.createToken(user);
        String accessToken = jwtService.generateAccessToken(user);

        platformUserService.updateLastLogin(user.getId());

        return LoginResponseDto.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .accessTokenExpiresAt(jwtService.getAccessTokenExpiryTime())
                .refreshToken(refreshToken.getToken())
                .refreshTokenExpiresAt(refreshToken.getExpiresAt())
                .user(mapUser(user))
                .build();
    }

    @Override
    public RefreshTokenResponseDto refreshAccessToken(RefreshTokenRequestDto requestDto) {
        String requestedTenantKey = resolveRequestedTenantKey(requestDto.getTenantKey());
        RefreshToken currentToken = refreshTokenService.validateTokenForRefresh(requestDto.getRefreshToken());
        PlatformUser user = currentToken.getPlatformUser();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InactiveUserException("User account is inactive");
        }

        validateRefreshTenantScope(currentToken, requestedTenantKey);

        RefreshToken rotatedToken = refreshTokenService.rotateToken(currentToken);
        String newAccessToken = jwtService.generateAccessToken(user);

        return RefreshTokenResponseDto.builder()
                .tokenType("Bearer")
                .accessToken(newAccessToken)
                .accessTokenExpiresAt(jwtService.getAccessTokenExpiryTime())
                .refreshToken(rotatedToken.getToken())
                .refreshTokenExpiresAt(rotatedToken.getExpiresAt())
                .build();
    }

    @Override
    public LogoutResponseDto logout(LogoutRequestDto requestDto) {
        String requestedTenantKey = resolveRequestedTenantKey(requestDto.getTenantKey());
        RefreshToken token = refreshTokenService.validateToken(requestDto.getRefreshToken());
        PlatformUser tokenUser = token.getPlatformUser();

        validateUserTenantScope(tokenUser, requestedTenantKey, true);

        PlatformUserPrincipal currentPrincipal = extractCurrentPrincipal();
        if (currentPrincipal == null || !currentPrincipal.getId().equals(tokenUser.getId())) {
            throw new ForbiddenOperationException("Cannot revoke a token for a different user");
        }

        refreshTokenService.revokeToken(requestDto.getRefreshToken());
        return LogoutResponseDto.builder()
                .revoked(true)
                .message("Logout successful. Refresh token revoked.")
                .build();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public AuthUserDto getCurrentUser() {
        PlatformUserPrincipal principal = extractCurrentPrincipal();
        if (principal == null) {
            throw new ForbiddenOperationException("No authenticated user found");
        }

        return AuthUserDto.builder()
                .id(principal.getId())
                .fullName(principal.getFullName())
                .email(principal.getUsername())
                .role(principal.getRole())
                .status(principal.getStatus())
                .tenantKey(principal.getTenantKey())
                .build();
    }

    private void validateUserTenantScope(
            PlatformUser user,
            String requestedTenantKey,
            boolean requireRequestedTenantKey) {
        if (!user.getRole().isTenantScoped()) {
            return;
        }

        String userTenantKey = normalizeTenantKey(user.getTenantKey());
        if (userTenantKey == null) {
            throw new ForbiddenOperationException("Tenant-scoped user must have a tenant key");
        }

        String normalizedRequestedTenant = normalizeTenantKey(requestedTenantKey);
        if (requireRequestedTenantKey && normalizedRequestedTenant == null) {
            throw new ForbiddenOperationException("Tenant key is required for tenant-scoped users");
        }
        if (normalizedRequestedTenant != null &&
                !normalizedRequestedTenant.equalsIgnoreCase(userTenantKey)) {
            throw new ForbiddenOperationException("Requested tenant does not match user tenant");
        }

        PlatformTenant tenant = masterTenantLookupService.findByTenantKey(userTenantKey)
                .orElseThrow(() -> new TenantNotFoundException(
                        "Tenant not found for user: " + userTenantKey));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new ForbiddenOperationException("Tenant is not active: " + userTenantKey);
        }
    }

    private void validateRefreshTenantScope(RefreshToken refreshToken, String requestedTenantKey) {
        PlatformUser user = refreshToken.getPlatformUser();
        validateUserTenantScope(user, requestedTenantKey, true);

        if (!user.getRole().isTenantScoped()) {
            return;
        }

        String tokenTenantKey = normalizeTenantKey(user.getTenantKey());
        String normalizedRequestedTenant = normalizeTenantKey(requestedTenantKey);
        if (tokenTenantKey == null || normalizedRequestedTenant == null ||
                !tokenTenantKey.equalsIgnoreCase(normalizedRequestedTenant)) {
            throw new ForbiddenOperationException("Refresh token tenant does not match the requested tenant");
        }
    }

    private PlatformUserPrincipal extractCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformUserPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private String normalizeTenantKey(String tenantKey) {
        if (tenantKey == null) {
            return null;
        }
        String normalized = tenantKey.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private String resolveRequestedTenantKey(String tenantKeyFromPayload) {
        String payloadTenantKey = normalizeTenantKey(tenantKeyFromPayload);
        String headerTenantKey = extractTenantKeyFromHeader();

        if (payloadTenantKey != null && headerTenantKey != null &&
                !payloadTenantKey.equalsIgnoreCase(headerTenantKey)) {
            throw new ForbiddenOperationException("Tenant key mismatch between payload and header");
        }

        return payloadTenantKey != null ? payloadTenantKey : headerTenantKey;
    }

    private String extractTenantKeyFromHeader() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }

        HttpServletRequest request = attributes.getRequest();
        if (request == null) {
            return null;
        }

        return normalizeTenantKey(request.getHeader(tenantHeaderName));
    }

    private AuthUserDto mapUser(PlatformUser user) {
        return AuthUserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .tenantKey(user.getTenantKey())
                .build();
    }
}
