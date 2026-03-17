package com.worknest.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MasterDataSourceConfig {

    @Value("${spring.datasource.url}")
    private String masterDbUrl;

    @Value("${spring.datasource.username}")
    private String masterDbUsername;

    @Value("${spring.datasource.password}")
    private String masterDbPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Bean(name = "masterDataSource")
    @Primary
    public DataSource masterDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(masterDbUrl);
        config.setUsername(masterDbUsername);
        config.setPassword(masterDbPassword);
        config.setDriverClassName(driverClassName);

        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("MasterPool");

        return new HikariDataSource(config);
    }

    @Bean(name = "masterJdbcTemplate")
    public JdbcTemplate masterJdbcTemplate() {
        return new JdbcTemplate(masterDataSource());
    }
}

