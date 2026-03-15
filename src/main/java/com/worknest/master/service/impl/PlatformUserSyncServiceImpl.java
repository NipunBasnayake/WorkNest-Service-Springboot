package com.worknest.master.service.impl;

import com.worknest.common.exception.DuplicateEmailException;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.master.service.PlatformUserSyncService;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.entity.Employee;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserSyncServiceImpl implements PlatformUserSyncService {

    private final PlatformUserRepository platformUserRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final PasswordEncoder passwordEncoder;

    public PlatformUserSyncServiceImpl(
            PlatformUserRepository platformUserRepository,
            MasterTenantContextRunner masterTenantContextRunner,
            PasswordEncoder passwordEncoder) {
        this.platformUserRepository = platformUserRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(
            transactionManager = "masterTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void syncOnCreate(Employee employee, String rawPassword, String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        masterTenantContextRunner.runInMasterContext(() -> {
            if (platformUserRepository.existsByEmailIgnoreCase(employee.getEmail())) {
                throw new DuplicateEmailException("Email already exists in platform users: " + employee.getEmail());
            }

            PlatformUser platformUser = new PlatformUser();
            platformUser.setFullName(employee.getFirstName() + " " + employee.getLastName());
            platformUser.setEmail(employee.getEmail());
            platformUser.setPasswordHash(resolvePasswordHash(rawPassword, employee.getPasswordHash()));
            platformUser.setRole(employee.getRole());
            platformUser.setStatus(employee.getStatus());
            platformUser.setTenantKey(normalizedTenantKey);

            platformUserRepository.save(platformUser);
        });
    }

    @Override
    @Transactional(
            transactionManager = "masterTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void syncOnUpdate(Employee employee, String oldEmail, String rawPassword, String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        masterTenantContextRunner.runInMasterContext(() -> {
            PlatformUser platformUser = platformUserRepository
                    .findByEmailIgnoreCaseAndTenantKey(normalizeEmail(oldEmail), normalizedTenantKey)
                    .orElseGet(() -> platformUserRepository
                            .findByEmailIgnoreCaseAndTenantKey(employee.getEmail(), normalizedTenantKey)
                            .orElse(null));

            if (platformUser == null) {
                PlatformUser newUser = new PlatformUser();
                newUser.setFullName(employee.getFirstName() + " " + employee.getLastName());
                newUser.setEmail(employee.getEmail());
                newUser.setPasswordHash(resolvePasswordHash(rawPassword, employee.getPasswordHash()));
                newUser.setRole(employee.getRole());
                newUser.setStatus(employee.getStatus());
                newUser.setTenantKey(normalizedTenantKey);

                if (platformUserRepository.existsByEmailIgnoreCase(newUser.getEmail())) {
                    throw new DuplicateEmailException("Email already exists in platform users: " + newUser.getEmail());
                }

                platformUserRepository.save(newUser);
                return;
            }

            if (!platformUser.getEmail().equalsIgnoreCase(employee.getEmail())) {
                if (platformUserRepository.existsByEmailIgnoreCase(employee.getEmail())) {
                    throw new DuplicateEmailException("Email already exists in platform users: " + employee.getEmail());
                }
                platformUser.setEmail(employee.getEmail());
            }

            platformUser.setFullName(employee.getFirstName() + " " + employee.getLastName());
            platformUser.setRole(employee.getRole());
            platformUser.setStatus(employee.getStatus());
            if (rawPassword != null && !rawPassword.isBlank()) {
                platformUser.setPasswordHash(passwordEncoder.encode(rawPassword));
            }

            platformUserRepository.save(platformUser);
        });
    }

    @Override
    @Transactional(
            transactionManager = "masterTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void syncStatus(Employee employee, String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        masterTenantContextRunner.runInMasterContext(() -> {
            PlatformUser platformUser = platformUserRepository
                    .findByEmailIgnoreCaseAndTenantKey(employee.getEmail(), normalizedTenantKey)
                    .orElse(null);
            if (platformUser != null) {
                platformUser.setStatus(employee.getStatus());
                platformUser.setRole(employee.getRole());
                platformUserRepository.save(platformUser);
            }
        });
    }

    private String resolvePasswordHash(String rawPassword, String existingHash) {
        if (rawPassword != null && !rawPassword.isBlank()) {
            return passwordEncoder.encode(rawPassword);
        }
        return existingHash;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeTenantKey(String tenantKey) {
        if (tenantKey == null) {
            return null;
        }
        String normalized = tenantKey.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}
