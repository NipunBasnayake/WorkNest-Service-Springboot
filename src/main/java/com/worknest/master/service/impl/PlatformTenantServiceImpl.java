package com.worknest.master.service.impl;

import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.dto.PlatformTenantUpdateRequestDto;
import com.worknest.master.dto.PlatformTenantResponseDto;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.PlatformTenantStatusAudit;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.PlatformTenantStatusAuditRepository;
import com.worknest.master.service.PlatformTenantService;
import com.worknest.master.service.PlatformTenantMetricsService;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.EnumSet;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "masterTransactionManager", readOnly = true)
public class PlatformTenantServiceImpl implements PlatformTenantService {

    private final PlatformTenantRepository tenantRepository;
    private final ModelMapper modelMapper;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final PlatformTenantStatusAuditRepository statusAuditRepository;
    private final SecurityUtils securityUtils;
    private final PlatformTenantMetricsService tenantMetricsService;
    private static final EnumSet<TenantStatus> ADMIN_MANAGED_STATUSES = EnumSet.of(
            TenantStatus.ACTIVE,
            TenantStatus.INACTIVE,
            TenantStatus.SUSPENDED,
            TenantStatus.ARCHIVED,
            TenantStatus.REJECTED);

    public PlatformTenantServiceImpl(
            PlatformTenantRepository tenantRepository,
            ModelMapper modelMapper,
            MasterTenantContextRunner masterTenantContextRunner,
            PlatformTenantStatusAuditRepository statusAuditRepository,
            SecurityUtils securityUtils,
            PlatformTenantMetricsService tenantMetricsService) {
        this.tenantRepository = tenantRepository;
        this.modelMapper = modelMapper;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.statusAuditRepository = statusAuditRepository;
        this.securityUtils = securityUtils;
        this.tenantMetricsService = tenantMetricsService;
    }

    @Override
    public List<PlatformTenantResponseDto> getAllTenants() {
        return masterTenantContextRunner.runInMasterContext(() -> tenantRepository.findAll().stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList()));
    }

    @Override
    public PlatformTenantResponseDto getTenantByKey(String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null) {
            throw new ResourceNotFoundException("Tenant key is required");
        }
        PlatformTenant tenant = masterTenantContextRunner.runInMasterContext(() -> tenantRepository.findByTenantKey(normalizedTenantKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with key: " + normalizedTenantKey)));
        return mapToResponseDto(tenant);
    }

    @Override
    public PlatformTenantResponseDto getTenantById(Long id) {
        PlatformTenant tenant = masterTenantContextRunner.runInMasterContext(() -> tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant not found with id: " + id)));
        return mapToResponseDto(tenant);
    }

    @Override
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    @Transactional(transactionManager = "masterTransactionManager")
    public PlatformTenantResponseDto updateTenant(String tenantKey, PlatformTenantUpdateRequestDto requestDto) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null) {
            throw new BadRequestException("Tenant key is required");
        }
        if (requestDto == null || requestDto.getCompanyName() == null || requestDto.getCompanyName().isBlank()) {
            throw new BadRequestException("Company name is required");
        }

        return masterTenantContextRunner.runInMasterContext(() -> {
            PlatformTenant tenant = tenantRepository.findByTenantKey(normalizedTenantKey)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant not found with key: " + normalizedTenantKey));
            tenant.setCompanyName(requestDto.getCompanyName().trim());
            PlatformTenant updated = tenantRepository.save(tenant);
            return mapToResponseDto(updated);
        });
    }

    @Override
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    @Transactional(transactionManager = "masterTransactionManager")
    public PlatformTenantResponseDto updateTenantStatus(String tenantKey, TenantStatus status) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null) {
            throw new BadRequestException("Tenant key is required");
        }
        if (status == null) {
            throw new BadRequestException("Tenant status is required");
        }
        if (!ADMIN_MANAGED_STATUSES.contains(status)) {
            throw new BadRequestException("Unsupported tenant lifecycle status");
        }

        return masterTenantContextRunner.runInMasterContext(() -> {
            PlatformTenant tenant = tenantRepository.findByTenantKey(normalizedTenantKey)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant not found with key: " + normalizedTenantKey));
            TenantStatus previousStatus = tenant.getStatus();
            if (previousStatus == status) {
                return mapToResponseDto(tenant);
            }
            if (!isAllowedTransition(previousStatus, status)) {
                throw new BadRequestException(
                        "Tenant lifecycle cannot change from " + previousStatus + " to " + status);
            }
            tenant.changeStatus(status);
            PlatformTenant updated = tenantRepository.saveAndFlush(tenant);
            statusAuditRepository.saveAndFlush(new PlatformTenantStatusAudit(
                    updated.getId(), updated.getTenantKey(), previousStatus, status,
                    securityUtils.getCurrentUserEmailOrThrow()));
            return mapToResponseDto(updated);
        });
    }

    @Override
    @CacheEvict(cacheNames = "platformOperations", allEntries = true)
    @Transactional(transactionManager = "masterTransactionManager")
    public void deleteTenant(String tenantKey) {
        updateTenantStatus(tenantKey, TenantStatus.ARCHIVED);
    }

    @Override
    public boolean tenantExists(String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null) {
            return false;
        }
        return masterTenantContextRunner.runInMasterContext(() -> tenantRepository.existsByTenantKey(normalizedTenantKey));
    }

    private PlatformTenantResponseDto mapToResponseDto(PlatformTenant tenant) {
        PlatformTenantResponseDto response = modelMapper.map(tenant, PlatformTenantResponseDto.class);
        PlatformTenantMetricsService.TenantMetrics metrics = tenantMetricsService.collect(tenant);
        response.setAdminName(metrics.adminName());
        response.setAdminEmail(metrics.adminEmail());
        response.setEmployeeCount(metrics.employeeCount());
        response.setProjectCount(metrics.projectCount());
        response.setTeamCount(metrics.teamCount());
        response.setTaskCount(metrics.taskCount());
        response.setLastLoginAt(metrics.lastLoginAt());
        response.setLastActivityAt(metrics.lastActivityAt());
        response.setUsageAvailable(metrics.usageAvailable());
        return response;
    }

    private String normalizeTenantKey(String tenantKey) {
        if (tenantKey == null) {
            return null;
        }
        String normalized = tenantKey.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isAllowedTransition(TenantStatus current, TenantStatus next) {
        if (current == null || next == null) return false;
        return switch (current) {
            case PROVISIONING -> next == TenantStatus.ACTIVE || next == TenantStatus.REJECTED;
            case ACTIVE -> next == TenantStatus.INACTIVE || next == TenantStatus.SUSPENDED || next == TenantStatus.ARCHIVED;
            case INACTIVE -> next == TenantStatus.ACTIVE || next == TenantStatus.SUSPENDED || next == TenantStatus.ARCHIVED;
            case SUSPENDED -> next == TenantStatus.ACTIVE || next == TenantStatus.INACTIVE || next == TenantStatus.ARCHIVED;
            case ARCHIVED, REJECTED -> next == TenantStatus.ACTIVE;
        };
    }
}
