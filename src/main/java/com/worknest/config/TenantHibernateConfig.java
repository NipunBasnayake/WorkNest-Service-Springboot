package com.worknest.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.worknest.tenant.repository",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class TenantHibernateConfig {

    @Bean
    public JpaVendorAdapter jpaVendorAdapter() {
        return new HibernateJpaVendorAdapter();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("masterDataSource") DataSource dataSource,
            MultiTenantConnectionProvider<String> multiTenantConnectionProvider,
            CurrentTenantIdentifierResolver<String> tenantIdentifierResolver,
            @Value("${app.tenant.jpa.hibernate.ddl-auto:update}") String ddlAuto,
            @Value("${spring.jpa.show-sql:false}") boolean showSql,
            @Value("${spring.jpa.properties.hibernate.format_sql:false}") boolean formatSql) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.worknest.tenant.entity");
        em.setPersistenceUnitName("tenant");
        em.setJpaVendorAdapter(jpaVendorAdapter());

        Map<String, Object> jpaPropertiesMap = new HashMap<>();

        // Multi-tenancy configuration (Hibernate 6.x style)
        jpaPropertiesMap.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, multiTenantConnectionProvider);
        jpaPropertiesMap.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        jpaPropertiesMap.put("hibernate.multiTenancy", "DATABASE");

        // Additional Hibernate settings (configurable via application.yml / env vars)
        jpaPropertiesMap.put(JdbcSettings.FORMAT_SQL, formatSql);
        jpaPropertiesMap.put(JdbcSettings.SHOW_SQL, showSql);
        jpaPropertiesMap.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        jpaPropertiesMap.put("hibernate.hbm2ddl.auto", ddlAuto);

        em.setJpaPropertyMap(jpaPropertiesMap);

        return em;
    }

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory tenantEntityManagerFactory) {
        return new JpaTransactionManager(tenantEntityManagerFactory);
    }
}

