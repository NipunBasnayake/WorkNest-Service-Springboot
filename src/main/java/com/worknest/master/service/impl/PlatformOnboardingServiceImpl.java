package com.worknest.master.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.DuplicateEmailException;
import com.worknest.common.exception.DuplicateTenantKeyException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.util.SlugUtils;
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
import com.worknest.master.service.TenantProvisioningService;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PlatformOnboardingServiceImpl implements PlatformOnboardingService {

    private static final Pattern TENANT_KEY_PATTERN = Pattern.compile("^[a-z0-9_-]{3,50}$");
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    private final PlatformTenantRepository platformTenantRepository;
    private final PlatformUserRepository platformUserRepository;
    private final TenantOnboardingRequestRepository onboardingRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final TenantProvisioningService tenantProvisioningService;
    private final TenantBrandingService tenantBrandingService;
    private final TransactionTemplate masterTransaction;
    private final String masterDbUrl;
    private final String masterDbUsername;
    private final String masterDbPassword;

    public PlatformOnboardingServiceImpl(
            PlatformTenantRepository platformTenantRepository,
            PlatformUserRepository platformUserRepository,
            TenantOnboardingRequestRepository onboardingRequestRepository,
            PasswordEncoder passwordEncoder,
            MasterTenantContextRunner masterTenantContextRunner,
            TenantProvisioningService tenantProvisioningService,
            TenantBrandingService tenantBrandingService,
            @Qualifier("masterTransactionManager") PlatformTransactionManager masterTransactionManager,
            @Value("${spring.datasource.url}") String masterDbUrl,
            @Value("${spring.datasource.username}") String masterDbUsername,
            @Value("${spring.datasource.password}") String masterDbPassword) {
        this.platformTenantRepository = platformTenantRepository;
        this.platformUserRepository = platformUserRepository;
        this.onboardingRequestRepository = onboardingRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.tenantProvisioningService = tenantProvisioningService;
        this.tenantBrandingService = tenantBrandingService;
        this.masterTransaction = new TransactionTemplate(masterTransactionManager);
        this.masterDbUrl = masterDbUrl;
        this.masterDbUsername = masterDbUsername;
        this.masterDbPassword = masterDbPassword;
    }

    @Override
    public TenantRegistrationResponseDto registerTenant(TenantRegistrationRequestDto requestDto) {
        return registerTenant(requestDto, null);
    }

    @Override
    public TenantRegistrationResponseDto registerTenant(
            TenantRegistrationRequestDto requestDto,
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

        Long tenantId = masterTenantContextRunner.runInMasterContext(() -> masterTransaction.execute(status ->
                persistRegistration(
                        requestDto,
                        normalizedTenantKey,
                        normalizedAdminEmail,
                        databaseName,
                        normalizedIdempotencyKey,
                        requestHash)));

        tenantProvisioningService.provisionTenant(tenantId);

        return masterTenantContextRunner.runInMasterContext(() -> masterTransaction.execute(status ->
                loadRegistrationResponse(tenantId)));
    }

    private Long persistRegistration(
            TenantRegistrationRequestDto requestDto,
            String normalizedTenantKey,
            String normalizedAdminEmail,
            String databaseName,
            String normalizedIdempotencyKey,
            String requestHash) {
        if (normalizedIdempotencyKey != null) {
            TenantOnboardingRequest existing = onboardingRequestRepository
                    .findByIdempotencyKey(normalizedIdempotencyKey)
                    .orElse(null);
            if (existing != null) {
                if (!existing.getRequestHash().equals(requestHash)) {
                    throw new IllegalArgumentException("Idempotency key was already used for a different request");
                }
                return existing.getTenant().getId();
            }
        }
        if (platformTenantRepository.existsByTenantKey(normalizedTenantKey)) {
            throw new DuplicateTenantKeyException("Tenant key already exists: " + normalizedTenantKey);
        }

        String companyName = requestDto.getCompanyName().trim();
        if (platformTenantRepository.existsByCompanyNameIgnoreCase(companyName)) {
            throw new IllegalArgumentException("Company is already registered: " + companyName);
        }
        if (platformUserRepository.existsByEmailIgnoreCase(normalizedAdminEmail)) {
            throw new DuplicateEmailException("Email already exists: " + normalizedAdminEmail);
        }

        PlatformTenant savedTenant = platformTenantRepository.save(
                buildTenantEntity(requestDto, normalizedTenantKey, databaseName));
        PlatformUser savedAdmin = platformUserRepository.save(
                buildTenantAdminEntity(requestDto, normalizedAdminEmail, normalizedTenantKey));
        tenantBrandingService.createDefaultBranding(
                savedTenant,
                requestDto.getPrimaryColor(),
                savedAdmin.getId());

        if (normalizedIdempotencyKey != null) {
            TenantOnboardingRequest onboardingRequest = new TenantOnboardingRequest();
            onboardingRequest.setIdempotencyKey(normalizedIdempotencyKey);
            onboardingRequest.setRequestHash(requestHash);
            onboardingRequest.setTenant(savedTenant);
            onboardingRequestRepository.save(onboardingRequest);
        }
        return savedTenant.getId();
    }

    private TenantRegistrationResponseDto loadRegistrationResponse(Long tenantId) {
        PlatformTenant tenant = platformTenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));
        PlatformUser admin = platformUserRepository
                .findFirstByTenantKeyIgnoreCaseAndRoleIn(
                        tenant.getTenantKey(),
                        List.of(PlatformRole.TENANT_ADMIN, PlatformRole.ADMIN))
                .orElse(null);
        var branding = tenantBrandingService.getPlatformBranding(tenant.getTenantKey());
        return buildResponse(tenant, admin, branding);
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
