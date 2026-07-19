package com.worknest.tenant.service.impl;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.common.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantDtoMapper {

    private final FileStorageService fileStorageService;

    public TenantDtoMapper() {
        this.fileStorageService = null;
    }

    @Autowired
    public TenantDtoMapper(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

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
                .avatarUrl(resolveAvatarUrl(employee))
                .role(employee.getRole())
                .status(employee.getStatus())
                .build();
    }

    private String resolveAvatarUrl(Employee employee) {
        if (fileStorageService == null) return null;
        return fileStorageService.toImageVariantUrl(employee.getAvatarAsset(), "64");
    }
}
