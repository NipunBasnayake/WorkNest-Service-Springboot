package com.worknest.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.JdbcSettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.worknest.master.repository",
        entityManagerFactoryRef = "masterEntityManagerFactory",
        transactionManagerRef = "masterTransactionManager"
)
public class MasterJpaConfig {

    @Bean(name = "masterEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean masterEntityManagerFactory(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("jpaVendorAdapter") JpaVendorAdapter jpaVendorAdapter,
            @Value("${spring.jpa.hibernate.ddl-auto:validate}") String ddlAuto,
            @Value("${spring.jpa.show-sql:false}") boolean showSql,
            @Value("${spring.jpa.properties.hibernate.format_sql:false}") boolean formatSql) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(masterDataSource);
        em.setPackagesToScan("com.worknest.master.entity");
        em.setPersistenceUnitName("master");
        em.setJpaVendorAdapter(jpaVendorAdapter);

        Map<String, Object> props = new HashMap<>();
        props.put(JdbcSettings.FORMAT_SQL, formatSql);
        props.put(JdbcSettings.SHOW_SQL, showSql);
        props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        props.put("hibernate.hbm2ddl.auto", ddlAuto);
        em.setJpaPropertyMap(props);

        return em;
    }

    @Bean(name = "masterTransactionManager")
    public PlatformTransactionManager masterTransactionManager(
            @Qualifier("masterEntityManagerFactory") EntityManagerFactory masterEntityManagerFactory) {
        return new JpaTransactionManager(masterEntityManagerFactory);
    }
}
