package com.worknest.config;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class TenantHibernateConfigTest {

    @Test
    void tenantPersistenceUnitDoesNotExposeMasterDataSourceToTenantTransactions() {
        TenantHibernateConfig config = new TenantHibernateConfig();
        @SuppressWarnings("unchecked")
        MultiTenantConnectionProvider<String> connectionProvider = interfaceStub(MultiTenantConnectionProvider.class);
        @SuppressWarnings("unchecked")
        CurrentTenantIdentifierResolver<String> tenantResolver = interfaceStub(CurrentTenantIdentifierResolver.class);

        LocalContainerEntityManagerFactoryBean factoryBean = config.entityManagerFactory(
                connectionProvider,
                tenantResolver,
                "none",
                false,
                false
        );

        assertThat(factoryBean.getDataSource()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static <T> T interfaceStub(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null
        );
    }
}
