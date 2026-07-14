package com.worknest.master.service;

import com.worknest.auth.service.AuthLoginThrottleService;
import com.worknest.auth.service.RefreshTokenService;
import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.dto.PlatformTenantAdminActionResponseDto;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class PlatformTenantAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%";
    private static final List<PlatformRole> ADMIN_ROLES = List.of(PlatformRole.TENANT_ADMIN, PlatformRole.ADMIN);

    private final PlatformTenantRepository tenantRepository;
    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthLoginThrottleService authLoginThrottleService;
    private final EmailNotificationService emailNotificationService;
    private final MasterTenantContextRunner masterTenantContextRunner;

    public PlatformTenantAdminService(
            PlatformTenantRepository tenantRepository,
            PlatformUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AuthLoginThrottleService authLoginThrottleService,
            EmailNotificationService emailNotificationService,
            MasterTenantContextRunner masterTenantContextRunner) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.authLoginThrottleService = authLoginThrottleService;
        this.emailNotificationService = emailNotificationService;
        this.masterTenantContextRunner = masterTenantContextRunner;
    }

    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public PlatformTenantAdminActionResponseDto resetPassword(String tenantKey) {
        PlatformUser admin = findTenantAdmin(tenantKey);
        String temporaryPassword = generateTemporaryPassword();
        masterTenantContextRunner.runInMasterContext(() -> {
            admin.setPasswordHash(passwordEncoder.encode(temporaryPassword));
            admin.setPasswordChangeRequired(true);
            admin.setPasswordResetTokenHash(null);
            admin.setPasswordResetTokenExpiresAt(null);
            userRepository.save(admin);
            return null;
        });
        refreshTokenService.revokeAllActiveTokens(admin);
        emailNotificationService.sendTemporaryPasswordEmailOrThrow(
                admin.getEmail(), admin.getFullName(), temporaryPassword);
        return new PlatformTenantAdminActionResponseDto(
                normalize(tenantKey), admin.getEmail(), "PASSWORD_RESET", true);
    }

    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    public PlatformTenantAdminActionResponseDto resendWelcome(String tenantKey) {
        PlatformUser admin = findTenantAdmin(tenantKey);
        if (admin.getLastLoginAt() != null) {
            throw new BadRequestException("Welcome credentials can only be resent before the tenant admin's first login");
        }
        PlatformTenantAdminActionResponseDto response = resetPassword(tenantKey);
        return new PlatformTenantAdminActionResponseDto(
                response.tenantKey(), response.adminEmail(), "WELCOME_RESENT", true);
    }

    public PlatformTenantAdminActionResponseDto unlock(String tenantKey) {
        PlatformUser admin = findTenantAdmin(tenantKey);
        authLoginThrottleService.recordSuccessfulLogin(admin.getEmail(), null);
        return new PlatformTenantAdminActionResponseDto(
                normalize(tenantKey), admin.getEmail(), "ACCOUNT_UNLOCKED", admin.isPasswordChangeRequired());
    }

    private PlatformUser findTenantAdmin(String tenantKey) {
        String normalizedTenantKey = normalize(tenantKey);
        if (normalizedTenantKey == null) throw new BadRequestException("Tenant key is required");
        return masterTenantContextRunner.runInMasterContext(() -> {
            if (!tenantRepository.existsByTenantKey(normalizedTenantKey)) {
                throw new ResourceNotFoundException("Tenant not found with key: " + normalizedTenantKey);
            }
            return userRepository.findFirstByTenantKeyIgnoreCaseAndRoleIn(normalizedTenantKey, ADMIN_ROLES)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant administrator was not found for: " + normalizedTenantKey));
        });
    }

    private String generateTemporaryPassword() {
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        StringBuilder password = new StringBuilder(14)
                .append(randomCharacter(UPPER))
                .append(randomCharacter(LOWER))
                .append(randomCharacter(DIGITS))
                .append(randomCharacter(SYMBOLS));
        while (password.length() < 14) password.append(randomCharacter(all));
        for (int index = password.length() - 1; index > 0; index--) {
            int swapIndex = SECURE_RANDOM.nextInt(index + 1);
            char current = password.charAt(index);
            password.setCharAt(index, password.charAt(swapIndex));
            password.setCharAt(swapIndex, current);
        }
        return password.toString();
    }

    private char randomCharacter(String characters) {
        return characters.charAt(SECURE_RANDOM.nextInt(characters.length()));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}
