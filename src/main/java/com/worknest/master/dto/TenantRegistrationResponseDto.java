package com.worknest.master.dto;

import com.worknest.common.enums.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRegistrationResponseDto {
    private Long tenantId;
    private String tenantKey;
    private String companyName;
    private String databaseName;
    private TenantStatus status;
    private Long tenantAdminUserId;
    private String tenantAdminEmail;
    private LocalDateTime createdAt;
}
