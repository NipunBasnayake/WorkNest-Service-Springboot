package com.worknest.master.service.impl;

import com.worknest.common.enums.TenantStatus;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Service
public class MasterTenantLookupServiceImpl implements MasterTenantLookupService {

    private final JdbcTemplate masterJdbcTemplate;

    public MasterTenantLookupServiceImpl(@Qualifier("masterJdbcTemplate") JdbcTemplate masterJdbcTemplate) {
        this.masterJdbcTemplate = masterJdbcTemplate;
    }

    @Override
    public Optional<PlatformTenant> findByTenantKey(String tenantKey) {
        String normalizedTenantKey = normalizeTenantKey(tenantKey);
        if (normalizedTenantKey == null) {
            return Optional.empty();
        }

        String sql = "SELECT id, tenant_key, company_name, database_name, db_url, db_username, " +
                     "db_password, status, created_at, updated_at " +
                     "FROM platform_tenants WHERE tenant_key = ?";

        try {
            PlatformTenant tenant = masterJdbcTemplate.queryForObject(sql, new TenantRowMapper(), normalizedTenantKey);
            return Optional.ofNullable(tenant);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private String normalizeTenantKey(String tenantKey) {
        if (tenantKey == null) {
            return null;
        }
        String normalized = tenantKey.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private static class TenantRowMapper implements RowMapper<PlatformTenant> {
        @Override
        public PlatformTenant mapRow(ResultSet rs, int rowNum) throws SQLException {
            PlatformTenant tenant = new PlatformTenant();
            tenant.setId(rs.getLong("id"));
            tenant.setTenantKey(rs.getString("tenant_key"));
            tenant.setCompanyName(rs.getString("company_name"));
            tenant.setDatabaseName(rs.getString("database_name"));
            tenant.setDbUrl(rs.getString("db_url"));
            tenant.setDbUsername(rs.getString("db_username"));
            tenant.setDbPassword(rs.getString("db_password"));
            tenant.setStatus(parseTenantStatus(rs.getString("status")));
            tenant.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            tenant.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            return tenant;
        }

        private TenantStatus parseTenantStatus(String rawStatus) {
            try {
                return TenantStatus.valueOf(rawStatus.toUpperCase());
            } catch (RuntimeException ex) {
                return TenantStatus.INACTIVE;
            }
        }
    }
}

