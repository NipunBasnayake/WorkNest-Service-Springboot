package com.worknest.auth.service.impl;

import com.worknest.auth.service.PlatformUserService;
import com.worknest.common.exception.DuplicateEmailException;
import com.worknest.common.exception.InvalidCredentialsException;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class PlatformUserServiceImpl implements PlatformUserService {

    private final PlatformUserRepository platformUserRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;

    public PlatformUserServiceImpl(
            PlatformUserRepository platformUserRepository,
            MasterTenantContextRunner masterTenantContextRunner) {
        this.platformUserRepository = platformUserRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public PlatformUser findByEmailOrThrow(String email) {
        return masterTenantContextRunner.runInMasterContext(() ->
                platformUserRepository.findByEmailIgnoreCase(normalizeEmail(email))
                        .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password")));
    }

    @Override
    public PlatformUser save(PlatformUser platformUser) {
        String normalizedEmail = normalizeEmail(platformUser.getEmail());
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Email is required");
        }
        platformUser.setEmail(normalizedEmail);

        if (emailExists(normalizedEmail)) {
            throw new DuplicateEmailException("Email already exists: " + normalizedEmail);
        }
        return masterTenantContextRunner.runInMasterContext(() -> platformUserRepository.save(platformUser));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public boolean emailExists(String email) {
        return masterTenantContextRunner.runInMasterContext(() ->
                platformUserRepository.existsByEmailIgnoreCase(normalizeEmail(email)));
    }

    @Override
    public void updateLastLogin(Long userId) {
        masterTenantContextRunner.runInMasterContext(() -> {
            PlatformUser user = platformUserRepository.findById(userId)
                    .orElseThrow(() -> new InvalidCredentialsException("User not found"));
            user.setLastLoginAt(LocalDateTime.now());
            platformUserRepository.save(user);
        });
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
