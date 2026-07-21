package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.tenant.dto.employee.EmployeeCreateRequestDto;
import com.worknest.tenant.dto.employee.EmployeeSkillCreateRequestDto;
import com.worknest.tenant.dto.employee.EmployeeStatusUpdateDto;
import com.worknest.tenant.dto.employee.EmployeeUpdateRequestDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.EmployeeSkill;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.EmployeeSkillRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.common.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void addSkillCanonicalizesWhitespaceAndCase() {
        Employee employee = employee(17L, 91L);
        when(employeeRepository.findById(17L)).thenReturn(Optional.of(employee));
        when(employeeSkillRepository.save(any(EmployeeSkill.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeSkillCreateRequestDto request = new EmployeeSkillCreateRequestDto();
        request.setSkillName("  REACT   native ");

        employeeService.addSkill(17L, request);

        ArgumentCaptor<EmployeeSkill> skillCaptor = ArgumentCaptor.forClass(EmployeeSkill.class);
        verify(employeeSkillRepository).save(skillCaptor.capture());
        assertThat(skillCaptor.getValue().getSkillName()).isEqualTo("React Native");
    }

    @Test
    void addSkillRejectsCaseInsensitiveDuplicate() {
        Employee employee = employee(17L, 91L);
        when(employeeRepository.findById(17L)).thenReturn(Optional.of(employee));
        when(employeeSkillRepository.existsByEmployeeAndSkillNameIgnoreCase(employee, "React"))
                .thenReturn(true);

        EmployeeSkillCreateRequestDto request = new EmployeeSkillCreateRequestDto();
        request.setSkillName(" REACT ");

        assertThatThrownBy(() -> employeeService.addSkill(17L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Skill already exists for this employee: React");
    }

    @Test
    void addSkillEnforcesTenSkillLimit() {
        Employee employee = employee(17L, 91L);
        when(employeeRepository.findById(17L)).thenReturn(Optional.of(employee));
        when(employeeSkillRepository.countByEmployeeId(17L)).thenReturn(10L);

        EmployeeSkillCreateRequestDto request = new EmployeeSkillCreateRequestDto();
        request.setSkillName("React");

        assertThatThrownBy(() -> employeeService.addSkill(17L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("An employee can have at most 10 skills");
    }

    @Test
    void createEmployeeRejectsDuplicateSkillsBeforeCrossDatabaseSync() {
        EmployeeSkillCreateRequestDto react = new EmployeeSkillCreateRequestDto();
        react.setSkillName("React");
        EmployeeSkillCreateRequestDto duplicate = new EmployeeSkillCreateRequestDto();
        duplicate.setSkillName(" REACT ");
        EmployeeCreateRequestDto request = new EmployeeCreateRequestDto();
        request.setRole(PlatformRole.EMPLOYEE);
        request.setEmail("employee@worknest.test");
        request.setSkills(List.of(react, duplicate));

        assertThatThrownBy(() -> employeeService.createEmployee(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Duplicate skill: React");

        verify(employeeRepository, never()).save(any(Employee.class));
        verifyNoInteractions(platformUserSyncBridgeService);
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
