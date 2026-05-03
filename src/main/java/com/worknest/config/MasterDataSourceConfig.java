package com.worknest.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MasterDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(MasterDataSourceConfig.class);

    @Value("${spring.datasource.url}")
    private String masterDbUrl;

    @Value("${spring.datasource.username}")
    private String masterDbUsername;

    @Value("${spring.datasource.password}")
    private String masterDbPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${app.master.datasource.pool.maximum-pool-size:15}")
    private int maximumPoolSize;

    @Value("${app.master.datasource.pool.minimum-idle:2}")
    private int minimumIdle;

    @Value("${app.master.datasource.pool.connection-timeout-ms:30000}")
    private long connectionTimeoutMs;

    @Value("${app.master.datasource.pool.idle-timeout-ms:300000}")
    private long idleTimeoutMs;

    @Value("${app.master.datasource.pool.max-lifetime-ms:1800000}")
    private long maxLifetimeMs;

    @Value("${app.master.datasource.pool.validation-timeout-ms:5000}")
    private long validationTimeoutMs;

    @Value("${app.master.datasource.pool.leak-detection-threshold-ms:0}")
    private long leakDetectionThresholdMs;

    @Bean(name = "masterDataSource")
    @Primary
    public DataSource masterDataSource() {
        validateMasterDataSourceProperties();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(masterDbUrl);
        config.setUsername(masterDbUsername);
        config.setPassword(masterDbPassword);
        config.setDriverClassName(driverClassName);

        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setIdleTimeout(idleTimeoutMs);
        config.setMaxLifetime(maxLifetimeMs);
        config.setValidationTimeout(validationTimeoutMs);
        if (leakDetectionThresholdMs > 0) {
            config.setLeakDetectionThreshold(leakDetectionThresholdMs);
        }
        config.setPoolName("WorkNestMasterPool");
        config.setRegisterMbeans(false);

        return new HikariDataSource(config);
    }

    @Bean(name = "masterJdbcTemplate")
    public JdbcTemplate masterJdbcTemplate() {
        return new JdbcTemplate(masterDataSource());
    }

    private void validateMasterDataSourceProperties() {
        if (masterDbUrl == null || masterDbUrl.isBlank()) {
            throw new IllegalStateException("spring.datasource.url must be configured for master database connectivity");
        }
        if (masterDbUsername == null || masterDbUsername.isBlank()) {
            throw new IllegalStateException("spring.datasource.username must be configured for master database connectivity");
        }

        if (masterDbPassword == null || masterDbPassword.isBlank()) {
            log.warn("Master DB password is blank. Set DB_PASSWORD for secured environments.");
        }
        if ("root".equalsIgnoreCase(masterDbUsername)) {
            log.warn("Master DB is configured with root user. Prefer a dedicated least-privileged DB account.");
        }
    }
}

