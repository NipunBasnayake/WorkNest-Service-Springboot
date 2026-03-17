package com.worknest.tenant.dto.analytics;

import com.worknest.common.enums.PlatformRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class EmployeeRoleDistributionDto {
    private PlatformRole role;
    private long count;
}
