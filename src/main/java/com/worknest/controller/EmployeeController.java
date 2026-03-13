package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.context.TenantContext;
import com.worknest.tenant.dto.EmployeeCreateDto;
import com.worknest.tenant.dto.EmployeeResponseDto;
import com.worknest.tenant.dto.EmployeeUpdateDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.service.EmployeeService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tenant/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final ModelMapper modelMapper;

    public EmployeeController(EmployeeService employeeService, ModelMapper modelMapper) {
        this.employeeService = employeeService;
        this.modelMapper = modelMapper;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        Map<String, String> data = new HashMap<>();
        data.put("status", "UP");
        data.put("currentTenant", TenantContext.getTenantId());
        data.put("message", "Multi-tenant system is running");
        return ResponseEntity.ok(ApiResponse.success("Health check successful", data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> createEmployee(
            @Valid @RequestBody EmployeeCreateDto createDto) {
        Employee employee = modelMapper.map(createDto, Employee.class);
        Employee created = employeeService.createEmployee(employee);
        EmployeeResponseDto responseDto = mapToResponseDto(created);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Employee created successfully", responseDto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmployeeResponseDto>>> getAllEmployees() {
        List<Employee> employees = employeeService.getAllEmployees();
        List<EmployeeResponseDto> responseDtos = employees.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(
                ApiResponse.success("Employees retrieved successfully", responseDtos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getEmployeeById(@PathVariable Long id) {
        Employee employee = employeeService.getEmployeeById(id);
        EmployeeResponseDto responseDto = mapToResponseDto(employee);
        return ResponseEntity.ok(
                ApiResponse.success("Employee retrieved successfully", responseDto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateDto updateDto) {
        Employee employee = modelMapper.map(updateDto, Employee.class);
        Employee updated = employeeService.updateEmployee(id, employee);
        EmployeeResponseDto responseDto = mapToResponseDto(updated);
        return ResponseEntity.ok(
                ApiResponse.success("Employee updated successfully", responseDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.ok(
                ApiResponse.success("Employee deleted successfully"));
    }

    @GetMapping("/department/{department}")
    public ResponseEntity<ApiResponse<List<EmployeeResponseDto>>> getEmployeesByDepartment(
            @PathVariable String department) {
        List<Employee> employees = employeeService.getEmployeesByDepartment(department);
        List<EmployeeResponseDto> responseDtos = employees.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(
                ApiResponse.success("Employees retrieved successfully", responseDtos));
    }

    @GetMapping("/tenant-info")
    public ResponseEntity<ApiResponse<Map<String, String>>> getTenantInfo() {
        Map<String, String> info = new HashMap<>();
        String tenantId = TenantContext.getTenantId();
        info.put("tenantId", tenantId != null ? tenantId : "null");
        info.put("threadName", Thread.currentThread().getName());
        info.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(ApiResponse.success("Tenant info retrieved", info));
    }

    private EmployeeResponseDto mapToResponseDto(Employee employee) {
        return modelMapper.map(employee, EmployeeResponseDto.class);
    }
}

