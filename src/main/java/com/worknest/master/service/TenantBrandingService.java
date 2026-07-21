package com.worknest.master.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.exception.StaleBrandingVersionException;
import com.worknest.common.storage.AssetObservability;
import com.worknest.master.dto.BrandingUpdateRequestDto;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.TenantBranding;
import com.worknest.master.entity.TenantBrandingAudit;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.TenantBrandingAuditRepository;
import com.worknest.master.repository.TenantBrandingRepository;
import com.worknest.multitenancy.context.TenantContextHolder;
import com.worknest.security.model.PlatformUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class TenantBrandingService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-F]{6}$");

    private final PlatformTenantRepository tenantRepository;
    private final TenantBrandingRepository brandingRepository;
    private final TenantBrandingAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final AssetObservability observability;

    public TenantBrandingService(
            PlatformTenantRepository tenantRepository,
            TenantBrandingRepository brandingRepository,
            TenantBrandingAuditRepository auditRepository,
            ObjectMapper objectMapper,
            AssetObservability observability) {
        this.tenantRepository = tenantRepository;
        this.brandingRepository = brandingRepository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.observability = observability;
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public TenantBrandingViewDto getPublicBranding(String tenantSlug) {
        return observability.timeBrandingBootstrap("public", () -> {
            PlatformTenant tenant = tenantRepository.findBySlug(normalizeIdentifier(tenantSlug))
                    .filter(this::isPublicTenant)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant branding not found"));
            return toView(resolveBranding(tenant));
        });
    }

    public TenantBrandingViewDto getCurrentTenantBranding() {
        return observability.timeBrandingBootstrap(
                "tenant", () -> getByTenantSlug(requireCurrentTenantSlug(), false));
    }

    public TenantBrandingViewDto getPlatformBranding(String tenantKey) {
        return observability.timeBrandingBootstrap("platform", () -> {
            PlatformTenant tenant = tenantByKey(tenantKey);
            return toView(getOrCreateBranding(tenant, null));
        });
    }

    public TenantBrandingViewDto createDefaultBranding(PlatformTenant tenant, String primaryColor, Long actorUserId) {
        TenantBranding branding = getOrCreateBranding(tenant, actorUserId);
        String normalizedColor = normalizeColor(primaryColor);
        if (!branding.getPrimaryColor().equals(normalizedColor)) {
            branding.setPrimaryColor(normalizedColor);
            branding.setUpdatedByUserId(actorUserId);
            branding = brandingRepository.saveAndFlush(branding);
            audit(tenant, "BRANDING_CREATED", actorUserId, Map.of("primaryColor", normalizedColor));
        }
        return toView(branding);
    }

    public TenantBrandingViewDto updateCurrentTenantBranding(BrandingUpdateRequestDto request, Long expectedVersion) {
        return update(tenantBySlug(requireCurrentTenantSlug()), request, expectedVersion);
    }

    public TenantBrandingViewDto updatePlatformBranding(
            String tenantKey,
            BrandingUpdateRequestDto request,
            Long expectedVersion) {
        return update(tenantByKey(tenantKey), request, expectedVersion);
    }

    private TenantBrandingViewDto getByTenantSlug(String tenantSlug, boolean allowInactive) {
        PlatformTenant tenant = tenantBySlug(tenantSlug);
        if (!allowInactive && !isPublicTenant(tenant)) {
            throw new ResourceNotFoundException("Tenant branding not found");
        }
        return toView(getOrCreateBranding(tenant, null));
    }

    private TenantBrandingViewDto update(
            PlatformTenant tenant,
            BrandingUpdateRequestDto request,
            Long expectedVersion) {
        if (request == null) throw new BadRequestException("Branding update is required");
        TenantBranding branding = getOrCreateBranding(tenant, currentActorId());
        requireVersion(branding, firstNonNull(expectedVersion, request.getBrandingVersion()));

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        if (request.getCompanyName() != null) {
            String companyName = request.getCompanyName().trim();
            if (companyName.isEmpty()) throw new BadRequestException("Company name is required");
            if (!companyName.equals(tenant.getCompanyName())) {
                if (tenantRepository.existsByCompanyNameIgnoreCase(companyName)) {
                    throw new BadRequestException("Company name is already registered: " + companyName);
                }
                auditDetails.put("companyNameFrom", tenant.getCompanyName());
                auditDetails.put("companyNameTo", companyName);
                tenant.setCompanyName(companyName);
                tenantRepository.save(tenant);
            }
        }
        if (request.getPrimaryColor() != null) {
            String primaryColor = normalizeColor(request.getPrimaryColor());
            if (!primaryColor.equals(branding.getPrimaryColor())) {
                auditDetails.put("primaryColorFrom", branding.getPrimaryColor());
                auditDetails.put("primaryColorTo", primaryColor);
                branding.setPrimaryColor(primaryColor);
            }
        }
        branding.setUpdatedByUserId(currentActorId());
        branding.setUpdatedAt(LocalDateTime.now());
        branding = brandingRepository.saveAndFlush(branding);
        if (!auditDetails.isEmpty()) audit(tenant, "BRANDING_UPDATED", currentActorId(), auditDetails);
        return toView(branding);
    }

    private TenantBrandingViewDto toView(TenantBranding branding) {
        PlatformTenant tenant = branding.getTenant();
        return new TenantBrandingViewDto(
                tenant.getId(),
                tenant.getTenantKey(),
                tenant.getSlug(),
                tenant.getCompanyName(),
                branding.getPrimaryColor(),
                safeVersion(branding.getVersion()),
                tenant.getStatus(),
                branding.getUpdatedAt()
        );
    }

    private TenantBranding resolveBranding(PlatformTenant tenant) {
        return brandingRepository.findByTenantId(tenant.getId())
                .orElseGet(() -> getOrCreateBranding(tenant, null));
    }

    private TenantBranding getOrCreateBranding(PlatformTenant tenant, Long actorUserId) {
        return brandingRepository.findByTenantId(tenant.getId()).orElseGet(() -> {
            TenantBranding branding = new TenantBranding();
            branding.setTenant(tenant);
            branding.setCreatedByUserId(actorUserId);
            branding.setUpdatedByUserId(actorUserId);
            return brandingRepository.saveAndFlush(branding);
        });
    }

    private PlatformTenant tenantBySlug(String slug) {
        return tenantRepository.findBySlug(normalizeIdentifier(slug))
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private PlatformTenant tenantByKey(String tenantKey) {
        return tenantRepository.findByTenantKey(normalizeIdentifier(tenantKey))
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }

    private String requireCurrentTenantSlug() {
        String slug = TenantContextHolder.getTenantSlug();
        if (slug == null || slug.isBlank()) throw new BadRequestException("Tenant context is required");
        return slug;
    }

    private boolean isPublicTenant(PlatformTenant tenant) {
        return tenant.getStatus() == TenantStatus.ACTIVE && Boolean.TRUE.equals(tenant.getActive());
    }

    private String normalizeIdentifier(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]{0,79}")) {
            throw new ResourceNotFoundException("Tenant not found");
        }
        return normalized;
    }

    private String normalizeColor(String value) {
        String normalized = value == null || value.isBlank()
                ? TenantBranding.DEFAULT_PRIMARY_COLOR
                : value.trim().toUpperCase(Locale.ROOT);
        if (!HEX_COLOR.matcher(normalized).matches()) {
            throw new BadRequestException("Primary color must use #RRGGBB format");
        }
        return normalized;
    }

    private void requireVersion(TenantBranding branding, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(safeVersion(branding.getVersion()))) {
            throw new StaleBrandingVersionException("Branding was changed by another user; refresh and retry");
        }
    }

    private Long safeVersion(Long version) {
        return version == null ? 0L : version;
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    private Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PlatformUserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }

    private void audit(PlatformTenant tenant, String action, Long actorUserId, Map<String, ?> details) {
        TenantBrandingAudit audit = new TenantBrandingAudit();
        audit.setTenant(tenant);
        audit.setAction(action);
        audit.setActorUserId(actorUserId);
        try {
            audit.setDetailsJson(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            audit.setDetailsJson("{}");
        }
        auditRepository.save(audit);
    }

}
