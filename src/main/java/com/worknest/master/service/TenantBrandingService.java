package com.worknest.master.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.exception.StaleBrandingVersionException;
import com.worknest.common.storage.ImageAssetProcessor;
import com.worknest.common.storage.AssetObservability;
import com.worknest.common.storage.FileStorageService;
import com.worknest.common.storage.StorageCategory;
import com.worknest.common.storage.StorageProvider;
import com.worknest.common.storage.StoredImageObjectSet;
import com.worknest.master.dto.BrandingLogoDto;
import com.worknest.master.dto.BrandingUpdateRequestDto;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.entity.MasterStoredAsset;
import com.worknest.master.entity.MasterStoredAssetVariant;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.TenantBrandAsset;
import com.worknest.master.entity.TenantBranding;
import com.worknest.master.entity.TenantBrandingAudit;
import com.worknest.master.enums.AssetLifecycleState;
import com.worknest.master.enums.BrandAssetPurpose;
import com.worknest.master.enums.BrandThemeVariant;
import com.worknest.master.repository.MasterStoredAssetRepository;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.TenantBrandAssetRepository;
import com.worknest.master.repository.TenantBrandingAuditRepository;
import com.worknest.master.repository.TenantBrandingRepository;
import com.worknest.multitenancy.context.TenantContextHolder;
import com.worknest.security.model.PlatformUserPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class TenantBrandingService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-F]{6}$");

    public enum UrlScope {
        PUBLIC,
        PLATFORM
    }

    public record BrandingResource(Resource resource, String filename, String contentType, String etag) {
    }

    private final PlatformTenantRepository tenantRepository;
    private final TenantBrandingRepository brandingRepository;
    private final MasterStoredAssetRepository assetRepository;
    private final TenantBrandAssetRepository brandAssetRepository;
    private final TenantBrandingAuditRepository auditRepository;
    private final FileStorageService fileStorageService;
    private final StorageProvider storageProvider;
    private final ObjectMapper objectMapper;
    private final AssetObservability observability;

    public TenantBrandingService(
            PlatformTenantRepository tenantRepository,
            TenantBrandingRepository brandingRepository,
            MasterStoredAssetRepository assetRepository,
            TenantBrandAssetRepository brandAssetRepository,
            TenantBrandingAuditRepository auditRepository,
            FileStorageService fileStorageService,
            StorageProvider storageProvider,
            ObjectMapper objectMapper,
            AssetObservability observability) {
        this.tenantRepository = tenantRepository;
        this.brandingRepository = brandingRepository;
        this.assetRepository = assetRepository;
        this.brandAssetRepository = brandAssetRepository;
        this.auditRepository = auditRepository;
        this.fileStorageService = fileStorageService;
        this.storageProvider = storageProvider;
        this.objectMapper = objectMapper;
        this.observability = observability;
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public TenantBrandingViewDto getPublicBranding(String tenantSlug) {
        return observability.timeBrandingBootstrap("public", () -> {
            PlatformTenant tenant = tenantRepository.findBySlug(normalizeIdentifier(tenantSlug))
                    .filter(this::isPublicTenant)
                    .orElseThrow(() -> new ResourceNotFoundException("Tenant branding not found"));
            return toView(resolveBranding(tenant), UrlScope.PUBLIC);
        });
    }

    public TenantBrandingViewDto getCurrentTenantBranding() {
        return observability.timeBrandingBootstrap(
                "tenant", () -> getByTenantSlug(requireCurrentTenantSlug(), UrlScope.PUBLIC, false));
    }

    public TenantBrandingViewDto getPlatformBranding(String tenantKey) {
        return observability.timeBrandingBootstrap("platform", () -> {
            PlatformTenant tenant = tenantByKey(tenantKey);
            return toView(getOrCreateBranding(tenant, null), UrlScope.PLATFORM);
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
        return toView(branding, UrlScope.PUBLIC);
    }

    public TenantBrandingViewDto updateCurrentTenantBranding(BrandingUpdateRequestDto request, Long expectedVersion) {
        return update(tenantBySlug(requireCurrentTenantSlug()), request, expectedVersion, UrlScope.PUBLIC);
    }

    public TenantBrandingViewDto updatePlatformBranding(
            String tenantKey,
            BrandingUpdateRequestDto request,
            Long expectedVersion) {
        return update(tenantByKey(tenantKey), request, expectedVersion, UrlScope.PLATFORM);
    }

    public TenantBrandingViewDto uploadCurrentTenantLogo(MultipartFile logo, Long expectedVersion) {
        return uploadLogo(tenantBySlug(requireCurrentTenantSlug()), logo, expectedVersion, UrlScope.PUBLIC);
    }

    public TenantBrandingViewDto uploadPlatformLogo(String tenantKey, MultipartFile logo, Long expectedVersion) {
        return uploadLogo(tenantByKey(tenantKey), logo, expectedVersion, UrlScope.PLATFORM);
    }

    public TenantBrandingViewDto uploadRegistrationLogo(PlatformTenant tenant, MultipartFile logo, Long actorUserId) {
        return uploadLogo(tenant, logo, null, UrlScope.PUBLIC, actorUserId);
    }

    public TenantBrandingViewDto deleteCurrentTenantLogo(Long expectedVersion) {
        return deleteLogo(tenantBySlug(requireCurrentTenantSlug()), expectedVersion, UrlScope.PUBLIC);
    }

    public TenantBrandingViewDto deletePlatformLogo(String tenantKey, Long expectedVersion) {
        return deleteLogo(tenantByKey(tenantKey), expectedVersion, UrlScope.PLATFORM);
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public BrandingResource getPublicLogoResource(String tenantSlug, String publicId, String variant) {
        PlatformTenant tenant = tenantRepository.findBySlug(normalizeIdentifier(tenantSlug))
                .filter(this::isPublicTenant)
                .orElseThrow(() -> new ResourceNotFoundException("Branding asset not found"));
        return resolveResource(tenant, publicId, variant);
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public BrandingResource getPlatformLogoResource(String tenantKey, String publicId, String variant) {
        return resolveResource(tenantByKey(tenantKey), publicId, variant);
    }

    private TenantBrandingViewDto getByTenantSlug(String tenantSlug, UrlScope scope, boolean allowInactive) {
        PlatformTenant tenant = tenantBySlug(tenantSlug);
        if (!allowInactive && !isPublicTenant(tenant)) {
            throw new ResourceNotFoundException("Tenant branding not found");
        }
        return toView(getOrCreateBranding(tenant, null), scope);
    }

    private TenantBrandingViewDto update(
            PlatformTenant tenant,
            BrandingUpdateRequestDto request,
            Long expectedVersion,
            UrlScope scope) {
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
        return toView(branding, scope);
    }

    private TenantBrandingViewDto uploadLogo(
            PlatformTenant tenant,
            MultipartFile logo,
            Long expectedVersion,
            UrlScope scope) {
        return uploadLogo(tenant, logo, expectedVersion, scope, currentActorId());
    }

    private TenantBrandingViewDto uploadLogo(
            PlatformTenant tenant,
            MultipartFile logo,
            Long expectedVersion,
            UrlScope scope,
            Long actorUserId) {
        TenantBranding branding = getOrCreateBranding(tenant, actorUserId);
        requireVersion(branding, expectedVersion);
        StoredImageObjectSet stored = fileStorageService.storeImageObjects(
                tenant.getSlug(),
                logo,
                ImageAssetProcessor.Profile.LOGO,
                StorageCategory.WORKSPACE_LOGO
        );

        MasterStoredAsset asset = new MasterStoredAsset();
        StoredImageObjectSet.StoredImageObject original = stored.original();
        asset.setPublicId(stored.publicId());
        asset.setTenant(tenant);
        asset.setObjectKey(original.relativePath());
        asset.setOriginalFilename(stored.originalFilename());
        asset.setExtension(original.extension());
        asset.setContentType(original.contentType());
        asset.setFileSize(original.fileSize());
        asset.setSha256(original.sha256());
        asset.setWidth(original.width());
        asset.setHeight(original.height());
        asset.setTransformationVersion(stored.transformationVersion());
        asset.setCreatedByUserId(actorUserId);
        for (StoredImageObjectSet.StoredImageObject storedVariant : stored.variants()) {
            MasterStoredAssetVariant variant = new MasterStoredAssetVariant();
            variant.setVariantName(storedVariant.name());
            variant.setObjectKey(storedVariant.relativePath());
            variant.setExtension(storedVariant.extension());
            variant.setContentType(storedVariant.contentType());
            variant.setFileSize(storedVariant.fileSize());
            variant.setSha256(storedVariant.sha256());
            variant.setWidth(storedVariant.width());
            variant.setHeight(storedVariant.height());
            asset.addVariant(variant);
        }
        asset = assetRepository.save(asset);

        brandAssetRepository.findFirstByTenantBrandingIdAndPurposeAndThemeVariantAndActiveTrue(
                branding.getId(), BrandAssetPurpose.LOGO, BrandThemeVariant.DEFAULT
        ).ifPresent(previous -> {
            previous.setActive(false);
            previous.getStoredAsset().setLifecycleState(AssetLifecycleState.SUPERSEDED);
            assetRepository.save(previous.getStoredAsset());
            brandAssetRepository.save(previous);
        });

        TenantBrandAsset association = new TenantBrandAsset();
        association.setTenantBranding(branding);
        association.setPurpose(BrandAssetPurpose.LOGO);
        association.setThemeVariant(BrandThemeVariant.DEFAULT);
        association.setStoredAsset(asset);
        association.setCreatedByUserId(actorUserId);
        brandAssetRepository.save(association);

        branding.setUpdatedByUserId(actorUserId);
        branding.setUpdatedAt(LocalDateTime.now());
        branding = brandingRepository.saveAndFlush(branding);
        audit(tenant, "LOGO_REPLACED", actorUserId, Map.of("assetId", stored.publicId()));
        return toView(branding, scope);
    }

    private TenantBrandingViewDto deleteLogo(PlatformTenant tenant, Long expectedVersion, UrlScope scope) {
        TenantBranding branding = getOrCreateBranding(tenant, currentActorId());
        requireVersion(branding, expectedVersion);
        brandAssetRepository.findFirstByTenantBrandingIdAndPurposeAndThemeVariantAndActiveTrue(
                branding.getId(), BrandAssetPurpose.LOGO, BrandThemeVariant.DEFAULT
        ).ifPresent(association -> {
            association.setActive(false);
            association.getStoredAsset().setLifecycleState(AssetLifecycleState.SUPERSEDED);
            assetRepository.save(association.getStoredAsset());
            brandAssetRepository.save(association);
        });
        branding.setUpdatedByUserId(currentActorId());
        branding.setUpdatedAt(LocalDateTime.now());
        branding = brandingRepository.saveAndFlush(branding);
        audit(tenant, "LOGO_REMOVED", currentActorId(), Map.of());
        return toView(branding, scope);
    }

    private TenantBrandingViewDto toView(TenantBranding branding, UrlScope scope) {
        PlatformTenant tenant = branding.getTenant();
        TenantBrandAsset association = brandAssetRepository
                .findFirstByTenantBrandingIdAndPurposeAndThemeVariantAndActiveTrue(
                        branding.getId(), BrandAssetPurpose.LOGO, BrandThemeVariant.DEFAULT)
                .orElse(null);
        BrandingLogoDto logo = association == null ? null : toLogoDto(tenant, association.getStoredAsset(), scope);
        return new TenantBrandingViewDto(
                tenant.getId(),
                tenant.getTenantKey(),
                tenant.getSlug(),
                tenant.getCompanyName(),
                branding.getPrimaryColor(),
                safeVersion(branding.getVersion()),
                branding.getTokenAlgorithmVersion(),
                logo,
                tenant.getStatus(),
                branding.getUpdatedAt()
        );
    }

    private BrandingLogoDto toLogoDto(PlatformTenant tenant, MasterStoredAsset asset, UrlScope scope) {
        String base = scope == UrlScope.PLATFORM
                ? "/api/platform/tenants/" + tenant.getTenantKey() + "/branding/assets/" + asset.getPublicId()
                : "/api/public/" + tenant.getSlug() + "/branding/assets/" + asset.getPublicId();
        Map<String, String> variants = new LinkedHashMap<>();
        asset.getVariants().stream()
                .sorted((left, right) -> left.getVariantName().compareTo(right.getVariantName()))
                .forEach(variant -> variants.put(variant.getVariantName(), base + "/" + variant.getVariantName()));
        return new BrandingLogoDto(
                asset.getPublicId(),
                base + "/original",
                Map.copyOf(variants),
                tenant.getCompanyName() + " logo",
                asset.getWidth(),
                asset.getHeight()
        );
    }

    private BrandingResource resolveResource(PlatformTenant tenant, String publicId, String requestedVariant) {
        TenantBranding branding = resolveBranding(tenant);
        TenantBrandAsset association = brandAssetRepository
                .findFirstByTenantBrandingIdAndPurposeAndThemeVariantAndActiveTrue(
                        branding.getId(), BrandAssetPurpose.LOGO, BrandThemeVariant.DEFAULT)
                .filter(value -> value.getStoredAsset().getPublicId().equals(publicId))
                .orElseThrow(() -> new ResourceNotFoundException("Branding asset not found"));
        MasterStoredAsset asset = association.getStoredAsset();
        if (asset.getLifecycleState() != AssetLifecycleState.ACTIVE || !asset.isPublicAsset()) {
            throw new ResourceNotFoundException("Branding asset not found");
        }
        String variantName = requestedVariant == null ? "original" : requestedVariant.toLowerCase(Locale.ROOT);
        if (variantName.equals("original")) {
            requireStoredObject(tenant.getSlug(), asset.getObjectKey(), asset.getSha256());
            return new BrandingResource(
                    storageProvider.read(tenant.getSlug(), asset.getObjectKey()),
                    asset.getOriginalFilename(),
                    asset.getContentType(),
                    '"' + asset.getSha256() + '"'
            );
        }
        MasterStoredAssetVariant variant = asset.getVariants().stream()
                .filter(value -> value.getVariantName().equals(variantName))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Branding asset not found"));
        requireStoredObject(tenant.getSlug(), variant.getObjectKey(), variant.getSha256());
        return new BrandingResource(
                storageProvider.read(tenant.getSlug(), variant.getObjectKey()),
                "logo-" + variantName + "." + variant.getExtension(),
                variant.getContentType(),
                '"' + variant.getSha256() + '"'
        );
    }

    private void requireStoredObject(String tenantSlug, String objectKey, String sha256) {
        if (!storageProvider.exists(tenantSlug, objectKey)
                || !storageProvider.hashMatches(tenantSlug, objectKey, sha256)) {
            observability.recordFallback("branding_object_missing");
            throw new ResourceNotFoundException("Branding asset not found");
        }
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
