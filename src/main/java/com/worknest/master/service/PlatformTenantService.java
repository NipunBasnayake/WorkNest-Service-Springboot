package com.worknest.master.service;

import com.worknest.master.dto.PlatformTenantResponseDto;
import com.worknest.common.enums.TenantStatus;

import java.util.List;

public interface PlatformTenantService {

    List<PlatformTenantResponseDto> getAllTenants();

    PlatformTenantResponseDto getTenantByKey(String tenantKey);

    PlatformTenantResponseDto getTenantById(Long id);

    PlatformTenantResponseDto updateTenantStatus(String tenantKey, TenantStatus status);

    boolean tenantExists(String tenantKey);
}

