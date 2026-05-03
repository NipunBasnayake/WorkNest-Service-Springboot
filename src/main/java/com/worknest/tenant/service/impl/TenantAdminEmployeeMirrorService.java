package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.context.TenantContext;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

@Service
public class TenantAdminEmployeeMirrorService {

    private static final Logger log = LoggerFactory.getLogger(TenantAdminEmployeeMirrorService.class);

    private final EmployeeRepository employeeRepository;
    private final PlatformUserRepository platformUserRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;

    public TenantAdminEmployeeMirrorService(
            EmployeeRepository employeeRepository,
            PlatformUserRepository platformUserRepository,
            MasterTenantContextRunner masterTenantContextRunner) {
        this.employeeRepository = employeeRepository;
        this.platformUserRepository = platformUserRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
    }

    public Employee ensureTenantAdminEmployeeMirror(String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null) {
            return null;
        }

        PlatformUser platformUser = findTenantAdminPlatformUser(normalizedTenantKey).orElse(null);
        if (platformUser == null) {
            log.warn("Skipping tenant admin employee mirror because no tenant admin platform user was found for {}",
                    normalizedTenantKey);
            return null;
        }

        return runInTenantContext(normalizedTenantKey, () -> upsertTenantAdminEmployee(platformUser, normalizedTenantKey));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED, transactionManager = "masterTransactionManager", readOnly = true)
    public Optional<PlatformUser> findTenantAdminPlatformUser(String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null) {
            return Optional.empty();
        }

        return masterTenantContextRunner.runInMasterContext(() ->
                platformUserRepository.findFirstByTenantKeyIgnoreCaseAndRoleIn(
                        normalizedTenantKey,
                        Arrays.asList(PlatformRole.TENANT_ADMIN, PlatformRole.ADMIN)));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED, transactionManager = "masterTransactionManager", readOnly = true)
    public Optional<PlatformUser> findTenantAdminPlatformUser(String tenantKey, Long platformUserId, String email) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedTenantKey == null) {
            return Optional.empty();
        }

        return masterTenantContextRunner.runInMasterContext(() -> {
            if (platformUserId != null) {
                PlatformUser byId = platformUserRepository.findById(platformUserId).orElse(null);
                if (byId != null
                        && normalizedTenantKey.equals(normalizeTenantKey(byId.getTenantKey()))
                        && byId.getRole() != null
                        && byId.getRole().isTenantAdminEquivalent()) {
                    return Optional.of(byId);
                }
            }

            if (normalizedEmail == null) {
                return Optional.empty();
            }

            return platformUserRepository.findByEmailIgnoreCaseAndTenantKey(normalizedEmail, normalizedTenantKey)
                    .filter(platformUser -> platformUser.getRole() != null
                            && platformUser.getRole().isTenantAdminEquivalent());
        });
    }

    public Employee provisionTenantAdminEmployee(PlatformUser platformUser, String tenantKey) {
        if (platformUser == null || platformUser.getRole() == null || !platformUser.getRole().isTenantAdminEquivalent()) {
            return null;
        }

        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null) {
            return null;
        }

        return runInTenantContext(normalizedTenantKey, () -> upsertTenantAdminEmployee(platformUser, normalizedTenantKey));
    }

    private Employee upsertTenantAdminEmployee(PlatformUser platformUser, String tenantKey) {
        Employee employee = employeeRepository.findByPlatformUserId(platformUser.getId()).orElse(null);
        if (employee == null) {
            employee = employeeRepository.findByEmailIgnoreCase(platformUser.getEmail()).orElse(null);
        }
        return upsertTenantAdminEmployee(platformUser, tenantKey, employee);
    }

    private Employee upsertTenantAdminEmployee(PlatformUser platformUser, String tenantKey, Employee employee) {
        Employee target = employee == null ? new Employee() : employee;
        boolean needsSave = employee == null;

        String normalizedEmail = normalizeEmail(platformUser.getEmail());
        if (!normalizedEmail.equalsIgnoreCase(nullSafe(target.getEmail()))) {
            target.setEmail(normalizedEmail);
            needsSave = true;
        }

        if (!platformUser.getId().equals(target.getPlatformUserId())) {
            target.setPlatformUserId(platformUser.getId());
            needsSave = true;
        }

        if (target.getEmployeeCode() == null || target.getEmployeeCode().isBlank()) {
            target.setEmployeeCode(resolveEmployeeCode(platformUser));
            needsSave = true;
        }

        String[] nameParts = splitFullName(platformUser.getFullName(), normalizedEmail);
        if (!nameParts[0].equals(target.getFirstName())) {
            target.setFirstName(nameParts[0]);
            needsSave = true;
        }
        if (!nameParts[1].equals(target.getLastName())) {
            target.setLastName(nameParts[1]);
            needsSave = true;
        }

        if (!platformUser.getPasswordHash().equals(target.getPasswordHash())) {
            target.setPasswordHash(platformUser.getPasswordHash());
            needsSave = true;
        }

        if (target.getRole() != PlatformRole.TENANT_ADMIN) {
            target.setRole(PlatformRole.TENANT_ADMIN);
            needsSave = true;
        }

        if (target.getStatus() != UserStatus.ACTIVE) {
            target.setStatus(UserStatus.ACTIVE);
            needsSave = true;
        }

        if (target.getJoinedDate() == null) {
            target.setJoinedDate(LocalDate.now());
            needsSave = true;
        }

        if (!needsSave) {
            return target;
        }

        Employee saved = employeeRepository.save(target);
        log.info("Tenant admin employee mirror ensured tenantKey={}, platformUserId={}, employeeId={}",
                tenantKey, platformUser.getId(), saved.getId());
        return saved;
    }

    private String resolveEmployeeCode(PlatformUser platformUser) {
        return "TA-" + (platformUser.getId() == null ? "0" : platformUser.getId());
    }

    private String[] splitFullName(String fullName, String fallbackEmail) {
        String normalizedFullName = fullName == null ? "" : fullName.trim();
        if (!normalizedFullName.isBlank()) {
            String[] parts = normalizedFullName.split("\\s+");
            if (parts.length == 1) {
                return new String[] { capitalize(parts[0]), "Admin" };
            }
            return new String[] { capitalize(parts[0]), String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)) };
        }

        String localPart = fallbackEmail;
        if (localPart != null) {
            int atIndex = localPart.indexOf('@');
            if (atIndex > -1) {
                localPart = localPart.substring(0, atIndex);
            }
        }

        String[] fallbackParts = (localPart == null ? "tenant admin" : localPart)
                .replace('.', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .split("\\s+");
        String firstName = fallbackParts.length == 0 || fallbackParts[0].isBlank()
                ? "Tenant"
                : capitalize(fallbackParts[0]);
        return new String[] { firstName, "Admin" };
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Tenant";
        }
        String normalized = value.trim();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1).toLowerCase();
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

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private <T> T runInTenantContext(String tenantKey, java.util.function.Supplier<T> supplier) {
        String previousTenant = TenantContext.getTenantId();
        try {
            TenantContext.setTenantId(tenantKey);
            return supplier.get();
        } finally {
            if (previousTenant == null || previousTenant.isBlank()) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
        }
    }
}