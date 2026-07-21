package com.worknest.tenant.dto.settings;

import com.worknest.common.enums.TenantStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class WorkspaceProfileResponseDto {
    private String tenantKey;
    private String companyName;
    private TenantStatus status;
}
