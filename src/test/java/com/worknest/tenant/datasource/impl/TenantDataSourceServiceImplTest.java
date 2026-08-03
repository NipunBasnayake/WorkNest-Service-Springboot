package com.worknest.tenant.datasource.impl;

import com.worknest.config.TenantDataSourceProperties;
import com.worknest.master.service.MasterTenantLookupService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TenantDataSourceServiceImplTest {

    @Test
    void acceptsMySqlMasterConfiguration() {
        assertThatCode(() -> service("jdbc:mysql://localhost:3306/platform_master"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNonMySqlMasterConfiguration() {
        assertThatThrownBy(() -> service("jdbc:h2:mem:test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MySQL JDBC URL");
    }

    private TenantDataSourceServiceImpl service(String url) {
        DataSourceProperties masterProperties = new DataSourceProperties();
        masterProperties.setUrl(url);
        masterProperties.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return new TenantDataSourceServiceImpl(
                mock(DataSource.class),
                mock(MasterTenantLookupService.class),
                "master",
                masterProperties,
                new TenantDataSourceProperties());
    }
}
