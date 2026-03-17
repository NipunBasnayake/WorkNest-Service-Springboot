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
    public PlatformUser syncOnCreate(Employee employee, String rawPassword, String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        return masterTenantContextRunner.runInMasterContext(() -> {
            String normalizedEmail = normalizeEmail(employee.getEmail());
            ensureEmailAvailable(normalizedEmail, null);
            PlatformUser platformUser = buildPlatformUser(
                    employee,
                    normalizedTenantKey,
                    resolvePasswordHash(rawPassword, employee.getPasswordHash())
            );
            return platformUserRepository.save(platformUser);
        });
    }

    @Override
    @Transactional(
            transactionManager = "masterTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public PlatformUser syncOnUpdate(Employee employee, String oldEmail, String rawPassword, String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        return masterTenantContextRunner.runInMasterContext(() ->
                upsertEmployeeUser(employee, oldEmail, rawPassword, normalizedTenantKey));
    }

    @Override
    @Transactional(
            transactionManager = "masterTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public PlatformUser syncStatus(Employee employee, String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        return masterTenantContextRunner.runInMasterContext(() ->
                upsertEmployeeUser(employee, employee.getEmail(), null, normalizedTenantKey));
    }

    @Override
    @Transactional(
            transactionManager = "masterTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public PlatformUser provisionEmployeeAccount(Employee employee, String rawPassword, String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        return masterTenantContextRunner.runInMasterContext(() ->
                upsertEmployeeUser(employee, employee.getEmail(), rawPassword, normalizedTenantKey));
    }

    private PlatformUser upsertEmployeeUser(
            Employee employee,
            String oldEmail,
            String rawPassword,
            String normalizedTenantKey) {

        PlatformUser platformUser = findExistingPlatformUser(employee, oldEmail, normalizedTenantKey);
        String normalizedEmployeeEmail = normalizeEmail(employee.getEmail());

        if (platformUser == null) {
            ensureEmailAvailable(normalizedEmployeeEmail, null);
            PlatformUser newUser = buildPlatformUser(
                    employee,
                    normalizedTenantKey,
                    resolvePasswordHash(rawPassword, employee.getPasswordHash())
            );
            return platformUserRepository.save(newUser);
        }

        if (!platformUser.getEmail().equalsIgnoreCase(normalizedEmployeeEmail)) {
            ensureEmailAvailable(normalizedEmployeeEmail, platformUser.getId());
            platformUser.setEmail(normalizedEmployeeEmail);
        }

        platformUser.setFullName(buildFullName(employee));
        platformUser.setRole(employee.getRole());
        platformUser.setStatus(employee.getStatus());
        platformUser.setTenantKey(normalizedTenantKey);
        if (rawPassword != null && !rawPassword.isBlank()) {
            platformUser.setPasswordHash(passwordEncoder.encode(rawPassword));
        }

        return platformUserRepository.save(platformUser);
    }

    private PlatformUser findExistingPlatformUser(
            Employee employee,
            String oldEmail,
            String normalizedTenantKey) {
        if (employee.getPlatformUserId() != null) {
            PlatformUser byId = platformUserRepository.findById(employee.getPlatformUserId()).orElse(null);
            if (byId != null && normalizedTenantKey.equals(normalizeTenantKey(byId.getTenantKey()))) {
                return byId;
            }
        }

        String normalizedOldEmail = normalizeEmail(oldEmail);
        PlatformUser byOldEmail = normalizedOldEmail == null
                ? null
                : platformUserRepository.findByEmailIgnoreCaseAndTenantKey(normalizedOldEmail, normalizedTenantKey)
                .orElse(null);
        if (byOldEmail != null) {
            return byOldEmail;
        }

        return platformUserRepository.findByEmailIgnoreCaseAndTenantKey(
                normalizeEmail(employee.getEmail()),
                normalizedTenantKey
        ).orElse(null);
    }

    private PlatformUser buildPlatformUser(Employee employee, String normalizedTenantKey, String passwordHash) {
        PlatformUser platformUser = new PlatformUser();
        platformUser.setFullName(buildFullName(employee));
        platformUser.setEmail(normalizeEmail(employee.getEmail()));
        platformUser.setPasswordHash(passwordHash);
        platformUser.setRole(employee.getRole());
        platformUser.setStatus(employee.getStatus());
        platformUser.setTenantKey(normalizedTenantKey);
        return platformUser;
    }

    private String buildFullName(Employee employee) {
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? employee.getEmail() : fullName;
    }

    private void ensureEmailAvailable(String normalizedEmail, Long existingUserId) {
        PlatformUser existingUser = platformUserRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (existingUser == null) {
            return;
        }
        if (existingUserId != null && existingUserId.equals(existingUser.getId())) {
            return;
        }
        throw new DuplicateEmailException("Email already exists in platform users: " + normalizedEmail);
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
