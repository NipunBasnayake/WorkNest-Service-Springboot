package com.worknest.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MasterDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "masterDataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource masterDataSource(
            @Qualifier("masterDataSourceProperties") DataSourceProperties properties) {
        validateMasterDataSourceProperties(properties);
        DatabaseDataSourceSupport.requireMySqlUrl(properties.getUrl());
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "masterJdbcTemplate")
    public JdbcTemplate masterJdbcTemplate(
            @Qualifier("masterDataSource") DataSource masterDataSource) {
        return new JdbcTemplate(masterDataSource);
    }

    private void validateMasterDataSourceProperties(DataSourceProperties properties) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            throw new IllegalStateException("spring.datasource.url must be configured for master database connectivity");
        }
        if (properties.getUsername() == null || properties.getUsername().isBlank()) {
            throw new IllegalStateException("spring.datasource.username must be configured for master database connectivity");
        }
        if (properties.getPassword() == null || properties.getPassword().isBlank()) {
            throw new IllegalStateException("spring.datasource.password must be configured for master database connectivity");
        }
    }
}
