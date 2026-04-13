package com.worknest.tenant.service.impl;

import com.worknest.master.entity.PlatformTenant;
import com.worknest.tenant.datasource.TenantDataSourceService;
import com.worknest.tenant.service.TenantSchemaService;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.cfg.JdbcSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Service
public class TenantSchemaServiceImpl implements TenantSchemaService {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaServiceImpl.class);

    private final TenantDataSourceService tenantDataSourceService;
    private final String ddlAuto;
    private final boolean showSql;
    private final boolean formatSql;

    public TenantSchemaServiceImpl(
            TenantDataSourceService tenantDataSourceService,
            @Value("${app.tenant.jpa.hibernate.ddl-auto:update}") String ddlAuto,
            @Value("${spring.jpa.show-sql:false}") boolean showSql,
            @Value("${spring.jpa.properties.hibernate.format_sql:false}") boolean formatSql) {
        this.tenantDataSourceService = tenantDataSourceService;
        this.ddlAuto = ddlAuto;
        this.showSql = showSql;
        this.formatSql = formatSql;
    }

    @Override
    public void ensureTenantSchema(PlatformTenant tenant) {
        DataSource tenantDataSource = tenantDataSourceService.createDataSource(tenant);
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        try {
            em.setDataSource(tenantDataSource);
            em.setPackagesToScan("com.worknest.tenant.entity");
            em.setPersistenceUnitName("tenant-schema-bootstrap-" + tenant.getTenantKey());
            em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

            Map<String, Object> jpaProps = new HashMap<>();
            jpaProps.put(JdbcSettings.SHOW_SQL, showSql);
            jpaProps.put(JdbcSettings.FORMAT_SQL, formatSql);
            jpaProps.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
            jpaProps.put("hibernate.hbm2ddl.auto", ddlAuto);
            em.setJpaPropertyMap(jpaProps);

            em.afterPropertiesSet();
            if (em.getObject() != null) {
                em.getObject().createEntityManager().close();
            }
            log.info("Hibernate tenant schema update applied for {} (ddl-auto={})", tenant.getTenantKey(), ddlAuto);
        } finally {
            try {
                em.destroy();
            } catch (Exception ignored) {
                // no-op
            }
            if (tenantDataSource instanceof HikariDataSource hikariDataSource) {
                hikariDataSource.close();
            }
        }
    }
}
