package com.worknest.config;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates the single recovery Platform Admin account for a fresh master database.
 * Existing administrators are deliberately left untouched.
 */
@Component
@Order(10)
public class PlatformAdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String name;
    private final String email;
    private final String password;

    public PlatformAdminBootstrap(
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${bootstrap.platform-admin.enabled:true}") boolean enabled,
            @Value("${bootstrap.platform-admin.name:Platform Admin}") String name,
            @Value("${bootstrap.platform-admin.email:platform.admin@worknest.local}") String email,
            @Value("${bootstrap.platform-admin.password:ChangeMe123!}") String password) {
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void run(String... args) {
        if (!enabled) {
            log.info("Platform Admin bootstrap is disabled");
            return;
        }

        if (platformUserRepository.existsByRole(PlatformRole.PLATFORM_ADMIN)) {
            log.info("Platform Admin bootstrap found an existing administrator");
            return;
        }

        String normalizedName = requireValue(name, "bootstrap.platform-admin.name");
        String normalizedEmail = requireValue(email, "bootstrap.platform-admin.email").toLowerCase(Locale.ROOT);
        String configuredPassword = requireValue(password, "bootstrap.platform-admin.password");

        if (platformUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException(
                    "Platform Admin bootstrap email is already assigned to a non-platform account: " + normalizedEmail);
        }

        PlatformUser platformAdmin = new PlatformUser();
        platformAdmin.setFullName(normalizedName);
        platformAdmin.setEmail(normalizedEmail);
        platformAdmin.setPasswordHash(passwordEncoder.encode(configuredPassword));
        platformAdmin.setRole(PlatformRole.PLATFORM_ADMIN);
        platformAdmin.setStatus(UserStatus.ACTIVE);
        platformAdmin.setTenantKey(null);
        platformAdmin.setPasswordChangeRequired(true);
        platformUserRepository.save(platformAdmin);

        log.info("✅ Platform Admin bootstrap created '{}'", normalizedEmail);
    }

    private String requireValue(String value, String propertyName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing required bootstrap property: " + propertyName);
        }
        return value.trim();
    }
}
