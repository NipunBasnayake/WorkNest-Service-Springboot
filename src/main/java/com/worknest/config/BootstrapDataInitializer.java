package com.worknest.config;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class BootstrapDataInitializer implements CommandLineRunner {

    private static final String DEFAULT_PLATFORM_ADMIN_NAME = "Platform Admin";
    private static final String DEFAULT_PLATFORM_ADMIN_EMAIL = "platform.admin@worknest.local";
    private static final String DEFAULT_PLATFORM_ADMIN_PASSWORD = "ChangeMe123!";

    private static final Logger log = LoggerFactory.getLogger(BootstrapDataInitializer.class);

    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final boolean bootstrapPlatformAdminEnabled;
    private final String bootstrapAdminEmail;
    private final String bootstrapAdminPassword;
    private final String bootstrapAdminName;

    public BootstrapDataInitializer(
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            MasterTenantContextRunner masterTenantContextRunner,
            @Value("${bootstrap.platform-admin.enabled:false}") boolean bootstrapPlatformAdminEnabled,
            @Value("${bootstrap.platform-admin.email:}") String bootstrapAdminEmail,
            @Value("${bootstrap.platform-admin.password:}") String bootstrapAdminPassword,
            @Value("${bootstrap.platform-admin.name:}") String bootstrapAdminName) {
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.bootstrapPlatformAdminEnabled = bootstrapPlatformAdminEnabled;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
        this.bootstrapAdminName = bootstrapAdminName;
    }

    @Override
    public void run(String... args) {
        masterTenantContextRunner.runInMasterContext(() -> {
            boolean platformAdminExists = platformUserRepository.existsByRole(PlatformRole.PLATFORM_ADMIN);
            if (platformAdminExists) {
                return;
            }

            boolean masterDatabaseEmpty = platformUserRepository.count() == 0;
            if (!bootstrapPlatformAdminEnabled && !masterDatabaseEmpty) {
                log.info("Bootstrap platform admin creation is disabled (bootstrap.platform-admin.enabled=false)");
                return;
            }

            String resolvedAdminName = resolveBootstrapValue(bootstrapAdminName, DEFAULT_PLATFORM_ADMIN_NAME);
            String resolvedAdminEmail = resolveBootstrapValue(bootstrapAdminEmail, DEFAULT_PLATFORM_ADMIN_EMAIL);
            String resolvedAdminPassword = resolveBootstrapValue(bootstrapAdminPassword, DEFAULT_PLATFORM_ADMIN_PASSWORD);

            if (isBlank(resolvedAdminEmail) || isBlank(resolvedAdminPassword) || isBlank(resolvedAdminName)) {
                throw new IllegalStateException(
                        "Bootstrap platform admin credentials are incomplete. " +
                                "Set BOOTSTRAP_PLATFORM_ADMIN_EMAIL, BOOTSTRAP_PLATFORM_ADMIN_PASSWORD and BOOTSTRAP_PLATFORM_ADMIN_NAME.");
            }

            if (masterDatabaseEmpty && !bootstrapPlatformAdminEnabled) {
                log.info("Master database is empty; seeding the default platform admin account");
            }

            if (platformUserRepository.existsByRole(PlatformRole.PLATFORM_ADMIN)) {
                return;
            }

            PlatformUser platformAdmin = new PlatformUser();
            platformAdmin.setFullName(resolvedAdminName.trim());
            platformAdmin.setEmail(resolvedAdminEmail.trim().toLowerCase());
            platformAdmin.setPasswordHash(passwordEncoder.encode(resolvedAdminPassword));
            platformAdmin.setRole(PlatformRole.PLATFORM_ADMIN);
            platformAdmin.setStatus(UserStatus.ACTIVE);
            platformAdmin.setTenantKey(null);

            platformUserRepository.save(platformAdmin);
            log.info("✅ Bootstrap PLATFORM_ADMIN created with email: {}", platformAdmin.getEmail());
        });
    }

    private String resolveBootstrapValue(String configuredValue, String defaultValue) {
        if (isBlank(configuredValue)) {
            return defaultValue;
        }
        return configuredValue;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
