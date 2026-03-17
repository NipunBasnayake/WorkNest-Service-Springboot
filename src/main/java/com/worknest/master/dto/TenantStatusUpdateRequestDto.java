package com.worknest.master.dto;

import com.worknest.common.enums.TenantStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantStatusUpdateRequestDto {

    @NotNull(message = "Tenant status is required")
    private TenantStatus status;
}
