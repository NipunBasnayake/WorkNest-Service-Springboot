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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapDataInitializer.class);

    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final String bootstrapAdminEmail;
    private final String bootstrapAdminPassword;
    private final String bootstrapAdminName;

    public BootstrapDataInitializer(
            PlatformUserRepository platformUserRepository,
            PasswordEncoder passwordEncoder,
            MasterTenantContextRunner masterTenantContextRunner,
            @Value("${app.bootstrap.platform-admin.email:platform.admin@worknest.local}") String bootstrapAdminEmail,
            @Value("${app.bootstrap.platform-admin.password:ChangeMe123!}") String bootstrapAdminPassword,
            @Value("${app.bootstrap.platform-admin.name:Platform Admin}") String bootstrapAdminName) {
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
        this.bootstrapAdminName = bootstrapAdminName;
    }

    @Override
    public void run(String... args) {
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
}
