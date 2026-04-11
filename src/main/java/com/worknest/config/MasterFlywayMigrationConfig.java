package com.worknest.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "app.master.migration.enabled", havingValue = "true")
public class MasterFlywayMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(MasterFlywayMigrationConfig.class);

    @Bean(initMethod = "migrate")
    public Flyway masterFlyway(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Value("${app.master.migration.locations:classpath:db/migration/master}") String locations,
            @Value("${app.master.migration.baseline-on-migrate:false}") boolean baselineOnMigrate,
            @Value("${app.master.migration.validate-on-migrate:true}") boolean validateOnMigrate) {
        log.info("Master Flyway migration is enabled. Ensure a proper baseline migration exists before production rollout.");
        return Flyway.configure()
                .dataSource(masterDataSource)
                .locations(locations.split(","))
                .baselineOnMigrate(baselineOnMigrate)
                .validateOnMigrate(validateOnMigrate)
                .load();
    }
}

