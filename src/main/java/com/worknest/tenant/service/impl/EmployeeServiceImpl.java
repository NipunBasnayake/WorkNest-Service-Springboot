package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.DuplicateEmailException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.util.EmployeeCodeGenerator;
import com.worknest.master.entity.PlatformUser;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.employee.*;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.EmployeeSkill;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.EmployeeSkillRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.EmployeeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.security.SecureRandom;

@Service
@Transactional(transactionManager = "transactionManager")
public class EmployeeServiceImpl implements EmployeeService {

    private static final int MAX_EMPLOYEE_CODE_GENERATION_ATTEMPTS = 100;
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String TEMP_PASSWORD_CHARSET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";
    private static final EnumSet<PlatformRole> TENANT_ASSIGNABLE_ROLES =
            EnumSet.of(PlatformRole.ADMIN, PlatformRole.MANAGER, PlatformRole.HR, PlatformRole.EMPLOYEE);

    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final PlatformUserSyncBridgeService platformUserSyncBridgeService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom;

    public EmployeeServiceImpl(
            EmployeeRepository employeeRepository,
            EmployeeSkillRepository employeeSkillRepository,
            PlatformUserSyncBridgeService platformUserSyncBridgeService,
            PasswordEncoder passwordEncoder,
            SecurityUtils securityUtils,
            AuditLogService auditLogService) {
        this.employeeRepository = employeeRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.platformUserSyncBridgeService = platformUserSyncBridgeService;
        this.passwordEncoder = passwordEncoder;
        this.securityUtils = securityUtils;
        this.auditLogService = auditLogService;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public EmployeeResponseDto createEmployee(EmployeeCreateRequestDto requestDto) {
        String email = normalizeEmail(requestDto.getEmail());
        validateTenantAssignableRole(requestDto.getRole());
        validateJoinedDate(requestDto.getJoinedDate());

        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException("Employee email already exists in this tenant: " + email);
        }

        String employeeCode = resolveEmployeeCode(requestDto.getEmployeeCode());

        Employee employee = new Employee();
        employee.setEmployeeCode(employeeCode);
        employee.setFirstName(requestDto.getFirstName().trim());
        employee.setLastName(requestDto.getLastName().trim());
        employee.setEmail(email);
        employee.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        employee.setRole(requestDto.getRole());
        employee.setDesignation(trimToNull(requestDto.getDesignation()));
        employee.setDepartment(trimToNull(requestDto.getDepartment()));
        employee.setPhone(trimToNull(requestDto.getPhone()));
        employee.setSalary(requestDto.getSalary());
        employee.setJoinedDate(requestDto.getJoinedDate());
        employee.setStatus(requestDto.getStatus() == null ? UserStatus.ACTIVE : requestDto.getStatus());

        Employee savedEmployee = employeeRepository.save(employee);
        String tenantKey = securityUtils.getCurrentTenantKeyOrThrow();

        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.EMPLOYEE,
                savedEmployee.getId(),
                "{\"email\":\"" + escapeJson(savedEmployee.getEmail()) + "\"}"
        );
        PlatformUser syncedUser = platformUserSyncBridgeService.syncOnCreate(
                savedEmployee,
                requestDto.getPassword(),
                tenantKey
        );
        savedEmployee = linkPlatformUser(savedEmployee, syncedUser);

        return toEmployeeResponse(savedEmployee);
    }

    @Override
    public EmployeeResponseDto updateEmployee(Long employeeId, EmployeeUpdateRequestDto requestDto) {
        Employee employee = getEmployeeOrThrow(employeeId);
        String oldEmail = employee.getEmail();

        String email = normalizeEmail(requestDto.getEmail());
        if (!employee.getEmail().equalsIgnoreCase(email) && employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException("Employee email already exists in this tenant: " + email);
        }

        if (requestDto.getRole() != null) {
            validateTenantAssignableRole(requestDto.getRole());
            employee.setRole(requestDto.getRole());
        }

        validateJoinedDate(requestDto.getJoinedDate());

        employee.setFirstName(requestDto.getFirstName().trim());
        employee.setLastName(requestDto.getLastName().trim());
        employee.setEmail(email);
        employee.setDesignation(trimToNull(requestDto.getDesignation()));
        employee.setDepartment(trimToNull(requestDto.getDepartment()));
        employee.setPhone(trimToNull(requestDto.getPhone()));
        employee.setSalary(requestDto.getSalary());

        if (requestDto.getJoinedDate() != null) {
            employee.setJoinedDate(requestDto.getJoinedDate());
        }
        if (requestDto.getStatus() != null) {
            employee.setStatus(requestDto.getStatus());
        }
        if (requestDto.getPassword() != null && !requestDto.getPassword().isBlank()) {
            employee.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        }

        Employee updated = employeeRepository.save(employee);
        String tenantKey = securityUtils.getCurrentTenantKeyOrThrow();

        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.EMPLOYEE,
                updated.getId(),
                "{\"email\":\"" + escapeJson(updated.getEmail()) + "\"}"
        );
        PlatformUser syncedUser = platformUserSyncBridgeService.syncOnUpdate(
                updated,
                oldEmail,
                requestDto.getPassword(),
                tenantKey
        );
        updated = linkPlatformUser(updated, syncedUser);

        return toEmployeeResponse(updated);
    }

    @Override
    public EmployeeResponseDto updateEmployeeStatus(Long employeeId, EmployeeStatusUpdateDto requestDto) {
        Employee employee = getEmployeeOrThrow(employeeId);
        employee.setStatus(requestDto.getStatus());
        Employee updated = employeeRepository.save(employee);
        String tenantKey = securityUtils.getCurrentTenantKeyOrThrow();

        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.EMPLOYEE,
                updated.getId(),
                "{\"status\":\"" + updated.getStatus() + "\"}"
        );
        PlatformUser syncedUser = platformUserSyncBridgeService.syncStatus(updated, tenantKey);
        updated = linkPlatformUser(updated, syncedUser);
        return toEmployeeResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public EmployeeResponseDto getEmployeeById(Long employeeId) {
        return toEmployeeResponse(getEmployeeOrThrow(employeeId));
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<EmployeeResponseDto> listEmployees(
            PlatformRole role,
            UserStatus status,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        validateListFilterRole(role);

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<Employee> employeePage = employeeRepository.search(
                role,
                status,
                trimToNull(search),
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        List<EmployeeResponseDto> items = employeePage.getContent().stream()
                .map(this::toEmployeeResponse)
                .toList();

        return PagedResultDto.<EmployeeResponseDto>builder()
                .items(items)
                .page(employeePage.getNumber())
                .size(employeePage.getSize())
                .totalElements(employeePage.getTotalElements())
                .totalPages(employeePage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public EmployeeResponseDto getMyProfile() {
        return toEmployeeResponse(resolveCurrentEmployeeOrThrow());
    }

    @Override
    public EmployeeResponseDto updateMyProfile(EmployeeSelfUpdateDto requestDto) {
        Employee employee = resolveCurrentEmployeeOrThrow();

        employee.setFirstName(requestDto.getFirstName().trim());
        employee.setLastName(requestDto.getLastName().trim());
        employee.setDesignation(trimToNull(requestDto.getDesignation()));
        employee.setPhone(trimToNull(requestDto.getPhone()));

        if (requestDto.getPassword() != null && !requestDto.getPassword().isBlank()) {
            employee.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        }

        Employee updated = employeeRepository.save(employee);
        String tenantKey = securityUtils.getCurrentTenantKeyOrThrow();
        PlatformUser syncedUser = platformUserSyncBridgeService.syncOnUpdate(
                updated,
                updated.getEmail(),
                requestDto.getPassword(),
                tenantKey
        );
        updated = linkPlatformUser(updated, syncedUser);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.EMPLOYEE,
                updated.getId(),
                "{\"selfService\":true}"
        );
        return toEmployeeResponse(updated);
    }

    @Override
    public EmployeeAccountProvisionResponseDto provisionEmployeeAccount(
            Long employeeId,
            EmployeeAccountProvisionRequestDto requestDto) {
        Employee employee = getEmployeeOrThrow(employeeId);
        validateTenantAssignableRole(employee.getRole());
        if (employee.getPlatformUserId() != null) {
            throw new BadRequestException("Employee login account is already provisioned");
        }

        String tenantKey = securityUtils.getCurrentTenantKeyOrThrow();
        String requestedPassword = requestDto == null ? null : trimToNull(requestDto.getTemporaryPassword());
        boolean generatedPassword = requestedPassword == null;
        String temporaryPassword = generatedPassword ? generateTemporaryPassword() : requestedPassword;

        PlatformUser provisionedUser = platformUserSyncBridgeService.provisionEmployeeAccount(
                employee,
                temporaryPassword,
                tenantKey
        );

        employee.setPasswordHash(provisionedUser.getPasswordHash());
        employee.setPlatformUserId(provisionedUser.getId());
        employee.setRole(provisionedUser.getRole());
        employee.setStatus(provisionedUser.getStatus());
        Employee updatedEmployee = employeeRepository.save(employee);

        auditLogService.logAction(
                AuditActionType.PROVISION,
                AuditEntityType.EMPLOYEE,
                updatedEmployee.getId(),
                "{\"platformUserId\":" + provisionedUser.getId() + "}"
        );

        return EmployeeAccountProvisionResponseDto.builder()
                .employeeId(updatedEmployee.getId())
                .platformUserId(provisionedUser.getId())
                .email(updatedEmployee.getEmail())
                .tenantKey(tenantKey)
                .role(updatedEmployee.getRole())
                .status(updatedEmployee.getStatus())
                .accountProvisioned(true)
                .temporaryPassword(generatedPassword ? temporaryPassword : null)
                .build();
    }

    @Override
    public EmployeeSkillResponseDto addSkill(Long employeeId, EmployeeSkillCreateRequestDto requestDto) {
        Employee employee = getEmployeeOrThrow(employeeId);
        String normalizedSkillName = normalizeSkillName(requestDto.getSkillName());

        if (employeeSkillRepository.existsByEmployeeAndSkillNameIgnoreCase(employee, normalizedSkillName)) {
            throw new BadRequestException("Skill already exists for this employee: " + normalizedSkillName);
        }

        EmployeeSkill skill = new EmployeeSkill();
        skill.setEmployee(employee);
        skill.setSkillName(normalizedSkillName);
        skill.setSkillLevel(requestDto.getSkillLevel());

        EmployeeSkill saved = employeeSkillRepository.save(skill);
        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.EMPLOYEE,
                employeeId,
                "{\"skill\":\"" + escapeJson(saved.getSkillName()) + "\"}"
        );
        return toSkillResponse(saved);
    }

    @Override
    public EmployeeSkillResponseDto updateSkill(Long employeeId, Long skillId, EmployeeSkillCreateRequestDto requestDto) {
        Employee employee = getEmployeeOrThrow(employeeId);
        String normalizedSkillName = normalizeSkillName(requestDto.getSkillName());

        EmployeeSkill existingSkill = employeeSkillRepository.findByIdAndEmployeeId(skillId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found for employee"));

        if (!existingSkill.getSkillName().equalsIgnoreCase(normalizedSkillName) &&
                employeeSkillRepository.existsByEmployeeAndSkillNameIgnoreCase(employee, normalizedSkillName)) {
            throw new BadRequestException("Skill already exists for this employee: " + normalizedSkillName);
        }

        existingSkill.setSkillName(normalizedSkillName);
        existingSkill.setSkillLevel(requestDto.getSkillLevel());

        EmployeeSkill updated = employeeSkillRepository.save(existingSkill);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.EMPLOYEE,
                employeeId,
                "{\"skill\":\"" + escapeJson(updated.getSkillName()) + "\"}"
        );
        return toSkillResponse(updated);
    }

    @Override
    public void deleteSkill(Long employeeId, Long skillId) {
        EmployeeSkill skill = employeeSkillRepository.findByIdAndEmployeeId(skillId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found for employee"));
        employeeSkillRepository.delete(skill);
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.EMPLOYEE,
                employeeId,
                "{\"skill\":\"" + escapeJson(skill.getSkillName()) + "\"}"
        );
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<EmployeeSkillResponseDto> listSkillsByEmployee(Long employeeId) {
        getEmployeeOrThrow(employeeId);
        return employeeSkillRepository.findByEmployeeIdOrderBySkillNameAsc(employeeId)
                .stream()
                .map(this::toSkillResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<EmployeeSkillResponseDto> listMySkills() {
        Employee currentEmployee = resolveCurrentEmployeeOrThrow();
        return employeeSkillRepository.findByEmployeeIdOrderBySkillNameAsc(currentEmployee.getId())
                .stream()
                .map(this::toSkillResponse)
                .toList();
    }

    private Employee getEmployeeOrThrow(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
    }

    private String resolveEmployeeCode(String requestedCode) {
        String normalizedRequestedCode = trimToNull(requestedCode);
        if (normalizedRequestedCode != null) {
            if (employeeRepository.existsByEmployeeCodeIgnoreCase(normalizedRequestedCode)) {
                throw new BadRequestException("Employee code already exists: " + normalizedRequestedCode);
            }
            return normalizedRequestedCode.toUpperCase();
        }

        int attempts = 0;
        while (attempts < MAX_EMPLOYEE_CODE_GENERATION_ATTEMPTS) {
            String generatedCode = EmployeeCodeGenerator.generateCode();
            if (!employeeRepository.existsByEmployeeCodeIgnoreCase(generatedCode)) {
                return generatedCode;
            }
            attempts++;
        }

        throw new BadRequestException("Unable to generate a unique employee code");
    }

    private void validateTenantAssignableRole(PlatformRole role) {
        if (role == null) {
            throw new BadRequestException("Role is required");
        }
        if (!isTenantAssignableRole(role)) {
            throw new BadRequestException("Only tenant business roles (ADMIN, MANAGER, HR, EMPLOYEE) are allowed");
        }
    }

    private boolean isTenantAssignableRole(PlatformRole role) {
        return role != null && TENANT_ASSIGNABLE_ROLES.contains(role);
    }

    private void validateListFilterRole(PlatformRole role) {
        if (role == null) {
            return;
        }
        if (!isTenantAssignableRole(role)) {
            throw new BadRequestException("Only tenant business roles can be used as employee filters");
        }
    }

    private void validateJoinedDate(LocalDate joinedDate) {
        if (joinedDate == null) {
            return;
        }
        if (joinedDate.isAfter(LocalDate.now())) {
            throw new BadRequestException("Joined date cannot be in the future");
        }
    }

    private EmployeeResponseDto toEmployeeResponse(Employee employee) {
        return EmployeeResponseDto.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .fullName(buildFullName(employee))
                .email(employee.getEmail())
                .platformUserId(employee.getPlatformUserId())
                .accountProvisioned(employee.getPlatformUserId() != null)
                .role(employee.getRole())
                .designation(employee.getDesignation())
                .department(employee.getDepartment())
                .phone(employee.getPhone())
                .salary(employee.getSalary())
                .joinedDate(employee.getJoinedDate())
                .status(employee.getStatus())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .build();
    }

    private EmployeeSkillResponseDto toSkillResponse(EmployeeSkill skill) {
        return EmployeeSkillResponseDto.builder()
                .id(skill.getId())
                .employeeId(skill.getEmployee().getId())
                .skillName(skill.getSkillName())
                .skillLevel(skill.getSkillLevel())
                .createdAt(skill.getCreatedAt())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalizeSkillName(String skillName) {
        String normalized = trimToNull(skillName);
        if (normalized == null) {
            throw new BadRequestException("Skill name is required");
        }
        return normalized;
    }

    private Employee linkPlatformUser(Employee employee, PlatformUser platformUser) {
        if (platformUser == null) {
            return employee;
        }
        boolean needsUpdate = false;

        if (platformUser.getId() != null && !platformUser.getId().equals(employee.getPlatformUserId())) {
            employee.setPlatformUserId(platformUser.getId());
            needsUpdate = true;
        }
        if (platformUser.getPasswordHash() != null &&
                !platformUser.getPasswordHash().equals(employee.getPasswordHash())) {
            employee.setPasswordHash(platformUser.getPasswordHash());
            needsUpdate = true;
        }

        if (!needsUpdate) {
            return employee;
        }
        return employeeRepository.save(employee);
    }

    private Employee resolveCurrentEmployeeOrThrow() {
        PlatformUserPrincipal principal = securityUtils.getCurrentPrincipalOrThrow();

        if (principal.getId() != null) {
            Employee employeeByPlatformId = employeeRepository.findByPlatformUserId(principal.getId()).orElse(null);
            if (employeeByPlatformId != null) {
                return employeeByPlatformId;
            }
        }

        String currentEmail = principal.getEmail();
        if (currentEmail == null || currentEmail.isBlank()) {
            throw new ResourceNotFoundException("No employee profile found for current user");
        }

        return employeeRepository.findByEmailIgnoreCase(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile found for current user"));
    }

    private String buildFullName(Employee employee) {
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        return (firstName + " " + lastName).trim();
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            int index = secureRandom.nextInt(TEMP_PASSWORD_CHARSET.length());
            password.append(TEMP_PASSWORD_CHARSET.charAt(index));
        }
        return password.toString();
    }

    private boolean isSortable(String sortBy) {
        return "createdAt".equals(sortBy) ||
                "updatedAt".equals(sortBy) ||
                "firstName".equals(sortBy) ||
                "lastName".equals(sortBy) ||
                "email".equals(sortBy) ||
                "employeeCode".equals(sortBy) ||
                "joinedDate".equals(sortBy) ||
                "department".equals(sortBy) ||
                "phone".equals(sortBy) ||
                "salary".equals(sortBy);
    }
}
