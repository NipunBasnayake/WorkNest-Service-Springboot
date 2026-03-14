package com.worknest.master.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.DuplicateEmailException;
import com.worknest.common.exception.DuplicateTenantKeyException;
import com.worknest.master.dto.TenantRegistrationRequestDto;
import com.worknest.master.dto.TenantRegistrationResponseDto;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.master.service.PlatformOnboardingService;
import com.worknest.tenant.datasource.TenantDataSourceService;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.regex.Pattern;

@Service
@Transactional
public class PlatformOnboardingServiceImpl implements PlatformOnboardingService {

    private static final Pattern TENANT_KEY_PATTERN = Pattern.compile("^[a-z0-9_-]{3,50}$");
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    private final PlatformTenantRepository platformTenantRepository;
    private final PlatformUserRepository platformUserRepository;
    private final JdbcTemplate masterJdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final TenantDataSourceService tenantDataSourceService;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final String masterDbUrl;
    private final String masterDbUsername;
    private final String masterDbPassword;

    public PlatformOnboardingServiceImpl(
            PlatformTenantRepository platformTenantRepository,
            PlatformUserRepository platformUserRepository,
            @Qualifier("masterJdbcTemplate") JdbcTemplate masterJdbcTemplate,
            PasswordEncoder passwordEncoder,
            TenantDataSourceService tenantDataSourceService,
            MasterTenantContextRunner masterTenantContextRunner,
            @Value("${spring.datasource.url}") String masterDbUrl,
            @Value("${spring.datasource.username}") String masterDbUsername,
            @Value("${spring.datasource.password}") String masterDbPassword) {
        this.platformTenantRepository = platformTenantRepository;
        this.platformUserRepository = platformUserRepository;
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tenantDataSourceService = tenantDataSourceService;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.masterDbUrl = masterDbUrl;
        this.masterDbUsername = masterDbUsername;
        this.masterDbPassword = masterDbPassword;
    }

    @Override
    public TenantRegistrationResponseDto registerTenant(TenantRegistrationRequestDto requestDto) {
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

        return masterTenantContextRunner.runInMasterContext(() -> {
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

            createTenantDatabaseIfMissing(databaseName);

            PlatformTenant tenant = buildTenantEntity(requestDto, normalizedTenantKey, databaseName);
            PlatformTenant savedTenant = platformTenantRepository.save(tenant);
            prepareTenantSchema(savedTenant);

            PlatformUser tenantAdmin = buildTenantAdminEntity(
                    requestDto,
                    normalizedAdminEmail,
                    normalizedTenantKey
            );
            PlatformUser savedAdmin = platformUserRepository.save(tenantAdmin);

            return TenantRegistrationResponseDto.builder()
                    .tenantId(savedTenant.getId())
                    .tenantKey(savedTenant.getTenantKey())
                    .companyName(savedTenant.getCompanyName())
                    .databaseName(savedTenant.getDatabaseName())
                    .status(savedTenant.getStatus())
                    .tenantAdminUserId(savedAdmin.getId())
                    .tenantAdminEmail(savedAdmin.getEmail())
                    .createdAt(savedTenant.getCreatedAt())
                    .build();
        });
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
        tenant.setStatus(TenantStatus.ACTIVE);
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

    private void createTenantDatabaseIfMissing(String databaseName) {
        masterJdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName + "`");
    }

    private void prepareTenantSchema(PlatformTenant tenant) {
        DataSource tenantDataSource = tenantDataSourceService.createDataSource(tenant);
        try {
            JdbcTemplate tenantJdbcTemplate = new JdbcTemplate(tenantDataSource);
            tenantJdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS employees (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        employee_code VARCHAR(20) NOT NULL UNIQUE,
                        first_name VARCHAR(100) NOT NULL,
                        last_name VARCHAR(100) NOT NULL,
                        email VARCHAR(255) NOT NULL UNIQUE,
                        phone VARCHAR(20),
                        position VARCHAR(100),
                        department VARCHAR(100),
                        salary DOUBLE,
                        hire_date DATETIME,
                        status VARCHAR(20) NOT NULL,
                        created_at DATETIME NOT NULL,
                        updated_at DATETIME NOT NULL
                    )
                    """);
        } finally {
            if (tenantDataSource instanceof HikariDataSource hikariDataSource) {
                hikariDataSource.close();
            }
        }
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
        return tenantKey == null ? null : tenantKey.trim().toLowerCase();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
