package com.worknest.tenant.service.impl;

import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.dto.settings.WorkspaceProfileResponseDto;
import com.worknest.tenant.dto.settings.WorkspaceProfileUpdateRequestDto;
import com.worknest.tenant.service.WorkspaceSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceSettingsServiceImpl implements WorkspaceSettingsService {

    private final SecurityUtils securityUtils;
    private final MasterTenantLookupService masterTenantLookupService;
    private final PlatformTenantRepository platformTenantRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;

    public WorkspaceSettingsServiceImpl(
            SecurityUtils securityUtils,
            MasterTenantLookupService masterTenantLookupService,
            PlatformTenantRepository platformTenantRepository,
            MasterTenantContextRunner masterTenantContextRunner) {
        this.securityUtils = securityUtils;
        this.masterTenantLookupService = masterTenantLookupService;
        this.platformTenantRepository = platformTenantRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public WorkspaceProfileResponseDto getWorkspaceProfile() {
        String tenantKey = securityUtils.getCurrentTenantKeyOrThrow();
        PlatformTenant tenant = masterTenantLookupService.findByTenantKey(tenantKey)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found for key: " + tenantKey));
        return toWorkspaceProfile(tenant);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public WorkspaceProfileResponseDto updateWorkspaceProfile(WorkspaceProfileUpdateRequestDto requestDto) {
        String tenantKey = securityUtils.getCurrentTenantKeyOrThrow();
        String companyName = normalizeCompanyName(requestDto.getCompanyName());

        return masterTenantContextRunner.runInMasterContext(() -> {
            PlatformTenant tenant = platformTenantRepository.findByTenantKey(tenantKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant not found for key: " + tenantKey));

            if (!tenant.getCompanyName().equalsIgnoreCase(companyName)
                    && platformTenantRepository.existsByCompanyNameIgnoreCase(companyName)) {
                throw new BadRequestException("Company name is already registered: " + companyName);
            }

            tenant.setCompanyName(companyName);
            PlatformTenant updated = platformTenantRepository.save(tenant);
            return toWorkspaceProfile(updated);
        });
    }

    private WorkspaceProfileResponseDto toWorkspaceProfile(PlatformTenant tenant) {
        return WorkspaceProfileResponseDto.builder()
                .tenantKey(tenant.getTenantKey())
                .companyName(tenant.getCompanyName())
                .status(tenant.getStatus())
                .build();
    }

    private String normalizeCompanyName(String companyName) {
        String normalized = companyName == null ? null : companyName.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Company name is required");
        }
        return normalized;
    }
}
