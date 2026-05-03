package com.worknest.security.service;

import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    private final SecurityUtils securityUtils;
    private final EmployeeRepository employeeRepository;

    public CurrentUserService(SecurityUtils securityUtils, EmployeeRepository employeeRepository) {
        this.securityUtils = securityUtils;
        this.employeeRepository = employeeRepository;
    }

    public Employee getCurrentEmployeeOrThrow() {
        Employee employee = getCurrentEmployeeOrNull();
        if (employee == null) {
            throw new ResourceNotFoundException("Current user does not have an employee profile");
        }
        return employee;
    }

    public Employee getCurrentEmployeeOrNull() {
        PlatformUserPrincipal principal = securityUtils.getCurrentPrincipalOrThrow();
        log.debug("Resolving current employee profile platformUserId={}, email={}, role={}, tenantKey={}",
                principal.getId(), principal.getEmail(), principal.getRole(), principal.getTenantKey());
        if (principal.getId() != null) {
            Employee byPlatformUser = employeeRepository.findByPlatformUserId(principal.getId()).orElse(null);
            if (byPlatformUser != null) {
                return byPlatformUser;
            }
        }

        String email = principal.getEmail();
        if (email == null || email.isBlank()) {
            return null;
        }
        Employee byEmail = employeeRepository.findByEmailIgnoreCase(email).orElse(null);
        if (byEmail != null) {
            return byEmail;
        }

        return null;
    }

    public Long getCurrentEmployeeIdOrThrow() {
        return getCurrentEmployeeOrThrow().getId();
    }

    public Long getCurrentEmployeeIdOrNull() {
        Employee employee = getCurrentEmployeeOrNull();
        return employee == null ? null : employee.getId();
    }

    public void requireCurrentEmployeeId(Long employeeId) {
        if (employeeId == null) {
            throw new ForbiddenOperationException("Employee ID is required");
        }
        Long currentEmployeeId = getCurrentEmployeeIdOrThrow();
        if (!currentEmployeeId.equals(employeeId)) {
            throw new ForbiddenOperationException("Authenticated user cannot act on behalf of another employee");
        }
    }
}
