package com.worknest.master.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.DuplicateEmailException;
import com.worknest.common.exception.DuplicateTenantKeyException;
import com.worknest.common.util.SlugUtils;
import com.worknest.master.event.TenantProvisioningRequestedEvent;
import com.worknest.master.dto.TenantRegistrationRequestDto;
import com.worknest.master.dto.TenantRegistrationResponseDto;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.TenantOnboardingRequest;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.master.repository.TenantOnboardingRequestRepository;
import com.worknest.master.service.PlatformOnboardingService;
import com.worknest.master.service.TenantBrandingService;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class PlatformOnboardingServiceImpl implements PlatformOnboardingService {

    private static final Pattern TENANT_KEY_PATTERN = Pattern.compile("^[a-z0-9_-]{3,50}$");
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    private final PlatformTenantRepository platformTenantRepository;
    private final PlatformUserRepository platformUserRepository;
    private final TenantOnboardingRequestRepository onboardingRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantBrandingService tenantBrandingService;
    private final String masterDbUrl;
    private final String masterDbUsername;
    private final String masterDbPassword;

    public PlatformOnboardingServiceImpl(
            PlatformTenantRepository platformTenantRepository,
            PlatformUserRepository platformUserRepository,
            TenantOnboardingRequestRepository onboardingRequestRepository,
            PasswordEncoder passwordEncoder,
            MasterTenantContextRunner masterTenantContextRunner,
            ApplicationEventPublisher eventPublisher,
            TenantBrandingService tenantBrandingService,
            @Value("${spring.datasource.url}") String masterDbUrl,
            @Value("${spring.datasource.username}") String masterDbUsername,
            @Value("${spring.datasource.password}") String masterDbPassword) {
        this.platformTenantRepository = platformTenantRepository;
        this.platformUserRepository = platformUserRepository;
        this.onboardingRequestRepository = onboardingRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.eventPublisher = eventPublisher;
        this.tenantBrandingService = tenantBrandingService;
        this.masterDbUrl = masterDbUrl;
        this.masterDbUsername = masterDbUsername;
        this.masterDbPassword = masterDbPassword;
    }

    @Override
    public TenantRegistrationResponseDto registerTenant(TenantRegistrationRequestDto requestDto) {
        return registerTenant(requestDto, null, null);
    }

    @Override
    public TenantRegistrationResponseDto registerTenant(
            TenantRegistrationRequestDto requestDto,
            MultipartFile logo,
            String idempotencyKey) {
        String normalizedTenantKey = normalizeTenantKey(requestDto.getTenantKey());
        if (normalizedTenantKey == null || !TENANT_KEY_PATTERN.matcher(normalizedTenantKey).matches()) {
            throw new IllegalArgumentException("Tenant key format is invalid");
        }

        String normalizedAdminEmail = normalizeEmail(requestDto.getAdminEmail());
        if (normalizedAdminEmail == null) {
            throw new IllegalArgumentException("Tenant admin email is required");
        }
        String databaseName = "tenant_" + normalizedTenantKey.replace('-', '_');
        validateDatabaseName(databaseName);
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(requestDto, normalizedTenantKey, normalizedAdminEmail);

        return masterTenantContextRunner.runInMasterContext(() -> {
            if (normalizedIdempotencyKey != null) {
                TenantOnboardingRequest existing = onboardingRequestRepository
                        .findByIdempotencyKey(normalizedIdempotencyKey)
                        .orElse(null);
                if (existing != null) {
                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new IllegalArgumentException("Idempotency key was already used for a different request");
                    }
                    PlatformUser existingAdmin = platformUserRepository
                            .findFirstByTenantKeyIgnoreCaseAndRoleIn(
                                    existing.getTenant().getTenantKey(),
                                    List.of(PlatformRole.TENANT_ADMIN, PlatformRole.ADMIN))
                            .orElse(null);
                    var branding = tenantBrandingService.getPlatformBranding(existing.getTenant().getTenantKey());
                    return buildResponse(existing.getTenant(), existingAdmin, branding);
                }
            }
            if (platformTenantRepository.existsByTenantKey(normalizedTenantKey)) {
                throw new DuplicateTenantKeyException("Tenant key already exists: " + normalizedTenantKey);
            }

            String companyName = requestDto.getCompanyName().trim();
            if (platformTenantRepository.existsByCompanyNameIgnoreCase(companyName)) {
                throw new IllegalArgumentException(
                        "Company is already registered: " + companyName);
            }

            if (platformUserRepository.existsByEmailIgnoreCase(normalizedAdminEmail)) {
                throw new DuplicateEmailException(
                        "Email already exists: " + normalizedAdminEmail);
            }

            PlatformTenant tenant = buildTenantEntity(requestDto, normalizedTenantKey, databaseName);
            PlatformTenant savedTenant = platformTenantRepository.save(tenant);

            PlatformUser tenantAdmin = buildTenantAdminEntity(
                    requestDto,
                    normalizedAdminEmail,
                    normalizedTenantKey
            );
            PlatformUser savedAdmin = platformUserRepository.save(tenantAdmin);
            var branding = tenantBrandingService.createDefaultBranding(
                    savedTenant,
                    requestDto.getPrimaryColor(),
                    savedAdmin.getId()
            );
            if (logo != null && !logo.isEmpty()) {
                branding = tenantBrandingService.uploadRegistrationLogo(savedTenant, logo, savedAdmin.getId());
            }
            if (normalizedIdempotencyKey != null) {
                TenantOnboardingRequest onboardingRequest = new TenantOnboardingRequest();
                onboardingRequest.setIdempotencyKey(normalizedIdempotencyKey);
                onboardingRequest.setRequestHash(requestHash);
                onboardingRequest.setTenant(savedTenant);
                onboardingRequestRepository.save(onboardingRequest);
            }
            eventPublisher.publishEvent(new TenantProvisioningRequestedEvent(savedTenant.getId()));
            return buildResponse(savedTenant, savedAdmin, branding);
        });
    }

    private TenantRegistrationResponseDto buildResponse(
            PlatformTenant tenant,
            PlatformUser admin,
            com.worknest.master.dto.TenantBrandingViewDto branding) {
        return TenantRegistrationResponseDto.builder()
                .tenantId(tenant.getId())
                .tenantKey(tenant.getTenantKey())
                .companyName(tenant.getCompanyName())
                .databaseName(tenant.getDatabaseName())
                .status(tenant.getStatus())
                .tenantAdminUserId(admin == null ? null : admin.getId())
                .tenantAdminEmail(admin == null ? null : admin.getEmail())
                .primaryColor(branding.primaryColor())
                .brandingVersion(branding.brandingVersion())
                .logoUrl(branding.logo() == null ? null : branding.logo().url())
                .createdAt(tenant.getCreatedAt())
                .build();
    }

    private PlatformTenant buildTenantEntity(
            TenantRegistrationRequestDto requestDto,
            String tenantKey,
            String databaseName) {

        PlatformTenant tenant = new PlatformTenant();
        tenant.setTenantKey(tenantKey);
        tenant.setCompanyName(requestDto.getCompanyName().trim());
        tenant.setDatabaseName(databaseName);
        tenant.setDbUrl(buildTenantDbUrl(databaseName));
        tenant.setDbUsername(masterDbUsername);
        tenant.setDbPassword(masterDbPassword);
        tenant.setStatus(TenantStatus.PROVISIONING);
        return tenant;
    }

    private PlatformUser buildTenantAdminEntity(
            TenantRegistrationRequestDto requestDto,
            String normalizedAdminEmail,
            String tenantKey) {

        PlatformUser tenantAdmin = new PlatformUser();
        tenantAdmin.setFullName(requestDto.getAdminFullName().trim());
        tenantAdmin.setEmail(normalizedAdminEmail);
        tenantAdmin.setPasswordHash(passwordEncoder.encode(requestDto.getAdminPassword()));
        tenantAdmin.setRole(PlatformRole.TENANT_ADMIN);
        tenantAdmin.setStatus(UserStatus.ACTIVE);
        tenantAdmin.setTenantKey(tenantKey);
        return tenantAdmin;
    }

    private String buildTenantDbUrl(String tenantDatabaseName) {
        int queryStartIndex = masterDbUrl.indexOf('?');
        String basePart = queryStartIndex > -1
                ? masterDbUrl.substring(0, queryStartIndex)
                : masterDbUrl;
        String queryPart = queryStartIndex > -1
                ? masterDbUrl.substring(queryStartIndex)
                : "?createDatabaseIfNotExist=true";

        int lastSlashIndex = basePart.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            throw new IllegalArgumentException("Master datasource URL format is invalid");
        }

        return basePart.substring(0, lastSlashIndex + 1) + tenantDatabaseName + queryPart;
    }

    private void validateDatabaseName(String databaseName) {
        if (!DATABASE_NAME_PATTERN.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Generated tenant database name is invalid");
        }
    }

    private String normalizeTenantKey(String tenantKey) {
        return SlugUtils.slugify(tenantKey);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 100 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Idempotency key is invalid");
        }
        return normalized;
    }

    private String requestHash(TenantRegistrationRequestDto request, String tenantKey, String adminEmail) {
        String canonical = request.getCompanyName().trim() + "\n" + tenantKey + "\n" + adminEmail + "\n"
                + (request.getPrimaryColor() == null ? "" : request.getPrimaryColor().trim().toUpperCase());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
