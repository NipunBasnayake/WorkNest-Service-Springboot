package com.worknest.master.dto;

import com.worknest.common.enums.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTenantResponseDto {

    private Long id;
    private String tenantKey;
    private String companyName;
    private String databaseName;
    private TenantStatus status;
    private Boolean active;
    private String adminName;
    private String adminEmail;
    private Long employeeCount;
    private Long projectCount;
    private Long teamCount;
    private Long taskCount;
    private LocalDateTime lastLoginAt;
    private LocalDateTime lastActivityAt;
    private Boolean usageAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

