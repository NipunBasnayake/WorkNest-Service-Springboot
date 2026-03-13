package com.worknest.master.dto;

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
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

