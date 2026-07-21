package com.worknest.tenant.service.impl;

import com.worknest.master.dto.BrandingUpdateRequestDto;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.service.TenantBrandingService;
import com.worknest.tenant.dto.settings.WorkspaceProfileResponseDto;
import com.worknest.tenant.dto.settings.WorkspaceProfileUpdateRequestDto;
import com.worknest.tenant.service.WorkspaceSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceSettingsServiceImpl implements WorkspaceSettingsService {

    private final TenantBrandingService tenantBrandingService;

    public WorkspaceSettingsServiceImpl(TenantBrandingService tenantBrandingService) {
        this.tenantBrandingService = tenantBrandingService;
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public WorkspaceProfileResponseDto getWorkspaceProfile() {
        return toWorkspaceProfile(tenantBrandingService.getCurrentTenantBranding());
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public WorkspaceProfileResponseDto updateWorkspaceProfile(WorkspaceProfileUpdateRequestDto requestDto) {
        BrandingUpdateRequestDto brandingUpdate = new BrandingUpdateRequestDto();
        brandingUpdate.setCompanyName(requestDto.getCompanyName());
        return toWorkspaceProfile(tenantBrandingService.updateCurrentTenantBranding(brandingUpdate, null));
    }

    private WorkspaceProfileResponseDto toWorkspaceProfile(TenantBrandingViewDto branding) {
        return WorkspaceProfileResponseDto.builder()
                .tenantKey(branding.tenantKey())
                .companyName(branding.companyName())
                .status(branding.status())
                .build();
    }

}
