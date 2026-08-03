package com.worknest.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseDataSourceSupportTest {

    @Test
    void acceptsMySqlJdbcUrls() {
        assertThat(DatabaseDataSourceSupport.isMySqlUrl("jdbc:mysql://localhost:3306/platform_master"))
                .isTrue();
        assertThat(DatabaseDataSourceSupport.isMySqlUrl("JDBC:MYSQL://localhost:3306/platform_master"))
                .isTrue();
    }

    @Test
    void rejectsNonMySqlJdbcUrls() {
        assertThatThrownBy(() -> DatabaseDataSourceSupport.requireMySqlUrl("jdbc:h2:mem:test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MySQL JDBC URL");
    }

    @Test
    void trimsBlankValuesToNull() {
        assertThat(DatabaseDataSourceSupport.trimToNull("  value  ")).isEqualTo("value");
        assertThat(DatabaseDataSourceSupport.trimToNull("   ")).isNull();
        assertThat(DatabaseDataSourceSupport.trimToNull(null)).isNull();
    }
}
