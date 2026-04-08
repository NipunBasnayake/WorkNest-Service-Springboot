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
            @Value("${app.bootstrap.platform-admin.enabled:false}") boolean bootstrapPlatformAdminEnabled,
            @Value("${app.bootstrap.platform-admin.email:}") String bootstrapAdminEmail,
            @Value("${app.bootstrap.platform-admin.password:}") String bootstrapAdminPassword,
            @Value("${app.bootstrap.platform-admin.name:}") String bootstrapAdminName) {
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
        if (!bootstrapPlatformAdminEnabled) {
            log.info("Bootstrap platform admin creation is disabled (app.bootstrap.platform-admin.enabled=false)");
            return;
        }
        if (isBlank(bootstrapAdminEmail) || isBlank(bootstrapAdminPassword) || isBlank(bootstrapAdminName)) {
            throw new IllegalStateException(
                    "Bootstrap platform admin is enabled but credentials are incomplete. " +
                            "Set BOOTSTRAP_PLATFORM_ADMIN_EMAIL, BOOTSTRAP_PLATFORM_ADMIN_PASSWORD and BOOTSTRAP_PLATFORM_ADMIN_NAME.");
        }

        masterTenantContextRunner.runInMasterContext(() -> {
            if (platformUserRepository.existsByRole(PlatformRole.PLATFORM_ADMIN)) {
                return;
            }

            PlatformUser platformAdmin = new PlatformUser();
            platformAdmin.setFullName(bootstrapAdminName.trim());
            platformAdmin.setEmail(bootstrapAdminEmail.trim().toLowerCase());
            platformAdmin.setPasswordHash(passwordEncoder.encode(bootstrapAdminPassword));
            platformAdmin.setRole(PlatformRole.PLATFORM_ADMIN);
            platformAdmin.setStatus(UserStatus.ACTIVE);
            platformAdmin.setTenantKey(null);

            platformUserRepository.save(platformAdmin);
            log.info("Bootstrap PLATFORM_ADMIN created with email: {}", platformAdmin.getEmail());
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
