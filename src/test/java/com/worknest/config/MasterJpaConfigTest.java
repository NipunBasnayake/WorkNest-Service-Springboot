package com.worknest.config;

import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MasterJpaConfigTest {

    @Test
    void masterPersistenceUnitUsesConfiguredHibernateSchemaUpdate() {
        MasterJpaConfig config = new MasterJpaConfig();

        LocalContainerEntityManagerFactoryBean factoryBean = config.masterEntityManagerFactory(
                mock(DataSource.class),
                new HibernateJpaVendorAdapter(),
                "update",
                false,
                false
        );

        assertThat(factoryBean.getJpaPropertyMap())
                .containsEntry("hibernate.hbm2ddl.auto", "update")
                .doesNotContainKey("hibernate.dialect");
    }
}
