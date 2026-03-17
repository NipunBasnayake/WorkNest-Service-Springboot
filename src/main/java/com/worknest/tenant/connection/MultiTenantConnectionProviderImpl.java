package com.worknest.tenant.connection;

import com.worknest.common.exception.TenantContextMissingException;
import com.worknest.common.exception.TenantResolutionException;
import com.worknest.common.util.AppConstants;
import com.worknest.tenant.datasource.TenantDataSourceService;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class MultiTenantConnectionProviderImpl implements MultiTenantConnectionProvider<String> {

    private static final String BOOTSTRAP_TENANT = "BOOTSTRAP";
    private final DataSource masterDataSource;
    private final TenantDataSourceService tenantDataSourceService;
    private final String defaultTenant;

    public MultiTenantConnectionProviderImpl(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            TenantDataSourceService tenantDataSourceService,
            @Value("${app.tenant.default:" + AppConstants.DEFAULT_TENANT + "}") String defaultTenant) {
        this.masterDataSource = masterDataSource;
        this.tenantDataSourceService = tenantDataSourceService;
        this.defaultTenant = defaultTenant;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        // Hibernate bootstrap/session validation may request a generic connection.
        // Always serve this from the master datasource.
        return masterDataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        try {
            if (BOOTSTRAP_TENANT.equalsIgnoreCase(tenantIdentifier)) {
                return masterDataSource.getConnection();
            }

            String normalizedTenantIdentifier = normalizeTenantIdentifier(tenantIdentifier);
            if (normalizedTenantIdentifier == null) {
                throw new TenantContextMissingException(
                        "Tenant identifier is missing for tenant-scoped connection resolution");
            }
            if (defaultTenant.equalsIgnoreCase(normalizedTenantIdentifier)) {
                throw new TenantContextMissingException(
                        "Master tenant identifier is not allowed for tenant-scoped connections");
            }

            // Get tenant-specific DataSource and return connection
            DataSource tenantDataSource = tenantDataSourceService.getDataSource(normalizedTenantIdentifier);
            return tenantDataSource.getConnection();
        } catch (TenantContextMissingException ex) {
            throw ex;
        } catch (Exception e) {
            throw new TenantResolutionException("Failed to get connection for tenant: " + tenantIdentifier, e);
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        if (unwrapType == null) {
            return false;
        }
        return unwrapType.isAssignableFrom(getClass())
                || MultiTenantConnectionProvider.class.equals(unwrapType);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return unwrapType.cast(this);
        }
        throw new IllegalArgumentException("Unwrap type is not supported: " + unwrapType);
    }

    private String normalizeTenantIdentifier(String tenantIdentifier) {
        if (tenantIdentifier == null) {
            return null;
        }
        String normalized = tenantIdentifier.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}

