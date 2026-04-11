package com.worknest.auth.service.impl;

import com.worknest.auth.dto.*;
import com.worknest.auth.service.AuthService;
import com.worknest.auth.service.PlatformUserService;
import com.worknest.auth.service.RefreshTokenService;
import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.*;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.RefreshToken;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.jwt.JwtService;
import com.worknest.security.model.PlatformUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final int PASSWORD_RESET_TOKEN_BYTES = 32;
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String TEMP_PASSWORD_CHARSET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";

    private final PlatformUserService platformUserService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MasterTenantLookupService masterTenantLookupService;
    private final PlatformUserRepository platformUserRepository;
    private final EmailNotificationService emailNotificationService;
    private final String tenantHeaderName;
    private final int passwordResetTokenExpiryMinutes;
    private final String passwordResetLinkBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(
            PlatformUserService platformUserService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            MasterTenantLookupService masterTenantLookupService,
            PlatformUserRepository platformUserRepository,
            EmailNotificationService emailNotificationService,
            @Value("${app.tenant.header:X-Tenant-ID}") String tenantHeaderName,
            @Value("${app.auth.password-reset.token-expiry-minutes:20}") int passwordResetTokenExpiryMinutes,
            @Value("${app.auth.password-reset.link-base-url:http://localhost:3000/reset-password}")
            String passwordResetLinkBaseUrl) {
        this.platformUserService = platformUserService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.masterTenantLookupService = masterTenantLookupService;
        this.platformUserRepository = platformUserRepository;
        this.emailNotificationService = emailNotificationService;
        this.tenantHeaderName = tenantHeaderName;
        this.passwordResetTokenExpiryMinutes = Math.max(15, Math.min(passwordResetTokenExpiryMinutes, 30));
        this.passwordResetLinkBaseUrl = passwordResetLinkBaseUrl;
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
                .refreshToken(resolveRefreshTokenValue(refreshToken))
                .refreshTokenExpiresAt(refreshToken.getExpiresAt())
                .passwordChangeRequired(user.isPasswordChangeRequired())
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
                .refreshToken(resolveRefreshTokenValue(rotatedToken))
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
                .passwordChangeRequired(principal.isPasswordChangeRequired())
                .build();
    }

    @Override
    public void forgotPassword(ForgotPasswordRequestDto requestDto) {
        String normalizedEmail = normalizeEmail(requestDto.getEmail());
        String requestedTenantKey = normalizeTenantKey(requestDto.getTenantKey());

        PlatformUser user = platformUserRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        if (!isForgotPasswordTenantScopeValid(user, requestedTenantKey)) {
            return;
        }

        String rawToken = generateSecureToken();
        user.setPasswordResetTokenHash(hashToken(rawToken));
        user.setPasswordResetTokenExpiresAt(LocalDateTime.now().plusMinutes(passwordResetTokenExpiryMinutes));
        platformUserRepository.save(user);

        try {
            String resetLink = buildResetLink(rawToken, user.getTenantKey());
            emailNotificationService.sendPasswordResetLinkEmailOrThrow(
                    user.getEmail(),
                    user.getFullName(),
                    resetLink,
                    passwordResetTokenExpiryMinutes
            );
        } catch (EmailServiceUnavailableException ex) {
            logger.warn("Forgot-password mail failed for user {}. Clearing generated token.", user.getId());
            clearPasswordResetToken(user);
            platformUserRepository.save(user);
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequestDto requestDto) {
        validatePasswordConfirmation(requestDto.getNewPassword(), requestDto.getConfirmPassword());

        String tokenHash = hashToken(requestDto.getToken().trim());
        PlatformUser user = platformUserRepository.findByPasswordResetTokenHash(tokenHash)
                .orElseThrow(() -> new PasswordResetTokenInvalidException("Password reset token is invalid"));

        if (user.getPasswordResetTokenExpiresAt() == null ||
                user.getPasswordResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PasswordResetTokenExpiredException("Password reset token has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(requestDto.getNewPassword()));
        user.setPasswordChangeRequired(false);
        clearPasswordResetToken(user);
        platformUserRepository.save(user);
        refreshTokenService.revokeAllActiveTokens(user);
    }

    @Override
    public void changePassword(ChangePasswordRequestDto requestDto) {
        validatePasswordConfirmation(requestDto.getNewPassword(), requestDto.getConfirmPassword());

        PlatformUserPrincipal principal = extractCurrentPrincipal();
        if (principal == null) {
            throw new ForbiddenOperationException("No authenticated user found");
        }

        PlatformUser user = platformUserRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(requestDto.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is invalid");
        }

        if (passwordEncoder.matches(requestDto.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(requestDto.getNewPassword()));
        user.setPasswordChangeRequired(false);
        clearPasswordResetToken(user);
        platformUserRepository.save(user);
        refreshTokenService.revokeAllActiveTokens(user);
    }

    @Override
    public ForceResetPasswordResponseDto forceResetPassword(Long userId) {
        PlatformUserPrincipal actor = extractCurrentPrincipal();
        if (actor == null) {
            throw new ForbiddenOperationException("No authenticated user found");
        }

        if (!(actor.getRole().isTenantAdminEquivalent() || actor.getRole().isHrEquivalent())) {
            throw new ForbiddenOperationException("Only TENANT_ADMIN or HR can force reset passwords");
        }

        String actorTenantKey = normalizeTenantKey(actor.getTenantKey());
        if (actorTenantKey == null) {
            throw new ForbiddenOperationException("Tenant key is required for force reset");
        }

        PlatformUser targetUser = platformUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!targetUser.getRole().isTenantScoped()) {
            throw new ForbiddenOperationException("Cannot force reset this account");
        }

        String targetTenantKey = normalizeTenantKey(targetUser.getTenantKey());
        if (targetTenantKey == null || !targetTenantKey.equalsIgnoreCase(actorTenantKey)) {
            throw new ForbiddenOperationException("Cannot force reset users from another tenant");
        }

        if (targetUser.getRole().isPlatformAdmin() || targetUser.getRole().isTenantAdminEquivalent()) {
            throw new ForbiddenOperationException("Force reset is allowed only for tenant employee accounts");
        }

        String temporaryPassword = generateTemporaryPassword();
        targetUser.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        targetUser.setPasswordChangeRequired(true);
        clearPasswordResetToken(targetUser);
        platformUserRepository.save(targetUser);
        refreshTokenService.revokeAllActiveTokens(targetUser);

        emailNotificationService.sendTemporaryPasswordEmailOrThrow(
                targetUser.getEmail(),
                targetUser.getFullName(),
                temporaryPassword
        );

        return ForceResetPasswordResponseDto.builder()
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .passwordChangeRequired(true)
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

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
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
                .passwordChangeRequired(user.isPasswordChangeRequired())
                .build();
    }

    private boolean isForgotPasswordTenantScopeValid(PlatformUser user, String requestedTenantKey) {
        if (!user.getRole().isTenantScoped()) {
            return true;
        }
        String userTenant = normalizeTenantKey(user.getTenantKey());
        if (userTenant == null) {
            return false;
        }
        if (requestedTenantKey == null) {
            return true;
        }
        return userTenant.equalsIgnoreCase(requestedTenantKey);
    }

    private String buildResetLink(String rawToken, String tenantKey) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(passwordResetLinkBaseUrl)
                .queryParam("token", rawToken);

        String normalizedTenant = normalizeTenantKey(tenantKey);
        if (normalizedTenant != null) {
            builder.queryParam("tenantKey", normalizedTenant);
        }

        return builder.toUriString();
    }

    private String generateSecureToken() {
        byte[] tokenBytes = new byte[PASSWORD_RESET_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private void validatePasswordConfirmation(String password, String confirmPassword) {
        if (password == null || confirmPassword == null || !password.equals(confirmPassword)) {
            throw new BadRequestException("Password and confirmPassword must match");
        }
    }

    private void clearPasswordResetToken(PlatformUser user) {
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetTokenExpiresAt(null);
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            int index = secureRandom.nextInt(TEMP_PASSWORD_CHARSET.length());
            password.append(TEMP_PASSWORD_CHARSET.charAt(index));
        }
        return password.toString();
    }

    private String resolveRefreshTokenValue(RefreshToken refreshToken) {
        if (refreshToken == null) {
            throw new InvalidTokenException("Refresh token is invalid");
        }
        String raw = refreshToken.getRawToken();
        if (raw != null && !raw.isBlank()) {
            return raw;
        }
        String legacy = refreshToken.getToken();
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }
        throw new InvalidTokenException("Refresh token is invalid");
    }
}
