package com.worknest.master.service;

import com.worknest.master.dto.PlatformTenantResponseDto;

import java.util.List;

public interface PlatformTenantService {

    List<PlatformTenantResponseDto> getAllTenants();

    PlatformTenantResponseDto getTenantByKey(String tenantKey);

    PlatformTenantResponseDto getTenantById(Long id);

    boolean tenantExists(String tenantKey);
}

