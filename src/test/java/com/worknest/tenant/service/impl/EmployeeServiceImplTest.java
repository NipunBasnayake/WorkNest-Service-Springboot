package com.worknest.tenant.service.impl;

import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.tenant.dto.employee.EmployeeStatusUpdateDto;
import com.worknest.tenant.dto.employee.EmployeeUpdateRequestDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.EmployeeSkillRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.common.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeSkillRepository employeeSkillRepository;
    @Mock private PlatformUserSyncBridgeService platformUserSyncBridgeService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditLogService auditLogService;
    @Mock private FileStorageService fileStorageService;

    private EmployeeServiceImpl employeeService;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeServiceImpl(
                employeeRepository,
                employeeSkillRepository,
                platformUserSyncBridgeService,
                passwordEncoder,
                authorizationService,
                auditLogService,
                fileStorageService
        );
    }

    @Test
    void statusEndpointRejectsSelfDeactivation() {
        Employee currentEmployee = employee(17L, 91L);
        when(employeeRepository.findById(17L)).thenReturn(Optional.of(currentEmployee));
        when(authorizationService.getCurrentEmployeeIdOrNull()).thenReturn(17L);

        EmployeeStatusUpdateDto request = new EmployeeStatusUpdateDto();
        request.setStatus(UserStatus.INACTIVE);

        assertThatThrownBy(() -> employeeService.updateEmployeeStatus(17L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot deactivate your own account.");
    }

    @Test
    void fullUpdateEndpointCannotBypassSelfDeactivationRule() {
        Employee currentEmployee = employee(17L, 91L);
        when(employeeRepository.findById(17L)).thenReturn(Optional.of(currentEmployee));
        when(authorizationService.getCurrentEmployeeIdOrNull()).thenReturn(null);
        when(authorizationService.getCurrentUserIdOrThrow()).thenReturn(91L);

        EmployeeUpdateRequestDto request = new EmployeeUpdateRequestDto();
        request.setEmail("admin@worknest.test");
        request.setStatus(UserStatus.INACTIVE);

        assertThatThrownBy(() -> employeeService.updateEmployee(17L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot deactivate your own account.");
    }

    private Employee employee(Long employeeId, Long platformUserId) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setPlatformUserId(platformUserId);
        employee.setEmail("admin@worknest.test");
        employee.setStatus(UserStatus.ACTIVE);
        return employee;
    }
}
