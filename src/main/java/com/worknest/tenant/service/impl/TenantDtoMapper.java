package com.worknest.tenant.service.impl;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.entity.Employee;
import org.springframework.stereotype.Component;

@Component
public class TenantDtoMapper {

    public EmployeeSimpleDto toEmployeeSimple(Employee employee) {
        if (employee == null) {
            return null;
        }
        return EmployeeSimpleDto.builder()
                .id(employee.getId())
                .platformUserId(employee.getPlatformUserId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFirstName() + " " + employee.getLastName())
                .email(employee.getEmail())
                .role(employee.getRole())
                .status(employee.getStatus())
                .build();
    }
}
