package com.worknest.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseDataSourceSupportTest {

    @Test
    void detectsPostgreSqlJdbcUrls() {
        assertThat(DatabaseDataSourceSupport.isPostgreSqlUrl("jdbc:postgresql://worknest-postgres:5432/platform_master"))
                .isTrue();
        assertThat(DatabaseDataSourceSupport.isPostgreSqlUrl("JDBC:POSTGRESQL://worknest-postgres:5432/platform_master"))
                .isTrue();
        assertThat(DatabaseDataSourceSupport.isPostgreSqlUrl("jdbc:h2:mem:test"))
                .isFalse();
    }

    @Test
    void trimsBlankValuesToNull() {
        assertThat(DatabaseDataSourceSupport.trimToNull("  value  ")).isEqualTo("value");
        assertThat(DatabaseDataSourceSupport.trimToNull("   ")).isNull();
        assertThat(DatabaseDataSourceSupport.trimToNull(null)).isNull();
    }
}
