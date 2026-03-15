package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.employee.*;
import com.worknest.tenant.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> createEmployee(
            @Valid @RequestBody EmployeeCreateRequestDto requestDto) {
        EmployeeResponseDto responseDto = employeeService.createEmployee(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Employee created successfully", responseDto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequestDto requestDto) {
        EmployeeResponseDto responseDto = employeeService.updateEmployee(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Employee updated successfully", responseDto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> updateEmployeeStatus(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeStatusUpdateDto requestDto) {
        EmployeeResponseDto responseDto = employeeService.updateEmployeeStatus(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Employee status updated successfully", responseDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getEmployeeById(@PathVariable Long id) {
        EmployeeResponseDto responseDto = employeeService.getEmployeeById(id);
        return ResponseEntity.ok(ApiResponse.success("Employee retrieved successfully", responseDto));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<PagedResultDto<EmployeeResponseDto>>> listEmployees(
            @RequestParam(required = false) PlatformRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        PagedResultDto<EmployeeResponseDto> responseDto = employeeService
                .listEmployees(role, status, search, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Employees retrieved successfully", responseDto));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getMyProfile() {
        EmployeeResponseDto responseDto = employeeService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.success("Employee profile retrieved", responseDto));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> updateMyProfile(
            @Valid @RequestBody EmployeeSelfUpdateDto requestDto) {
        EmployeeResponseDto responseDto = employeeService.updateMyProfile(requestDto);
        return ResponseEntity.ok(ApiResponse.success("Employee profile updated", responseDto));
    }

    @PostMapping("/{id}/skills")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<EmployeeSkillResponseDto>> addSkill(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeSkillCreateRequestDto requestDto) {
        EmployeeSkillResponseDto responseDto = employeeService.addSkill(id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Employee skill added successfully", responseDto));
    }

    @PutMapping("/{id}/skills/{skillId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<EmployeeSkillResponseDto>> updateSkill(
            @PathVariable Long id,
            @PathVariable Long skillId,
            @Valid @RequestBody EmployeeSkillCreateRequestDto requestDto) {
        EmployeeSkillResponseDto responseDto = employeeService.updateSkill(id, skillId, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Employee skill updated successfully", responseDto));
    }

    @DeleteMapping("/{id}/skills/{skillId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<Void>> deleteSkill(@PathVariable Long id, @PathVariable Long skillId) {
        employeeService.deleteSkill(id, skillId);
        return ResponseEntity.ok(ApiResponse.success("Employee skill deleted successfully"));
    }

    @GetMapping("/{id}/skills")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<List<EmployeeSkillResponseDto>>> listSkills(@PathVariable Long id) {
        List<EmployeeSkillResponseDto> response = employeeService.listSkillsByEmployee(id);
        return ResponseEntity.ok(ApiResponse.success("Employee skills retrieved successfully", response));
    }
}
