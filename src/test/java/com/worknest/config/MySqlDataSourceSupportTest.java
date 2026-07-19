package com.worknest.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDataSourceSupportTest {

    @Test
    void appendsSafeZeroDateHandlingToJdbcUrls() {
        assertThat(MySqlDataSourceSupport.withSafeZeroDateHandling("jdbc:mysql://localhost/worknest"))
                .isEqualTo("jdbc:mysql://localhost/worknest?zeroDateTimeBehavior=CONVERT_TO_NULL");
        assertThat(MySqlDataSourceSupport.withSafeZeroDateHandling("jdbc:mysql://localhost/worknest?useSSL=false"))
                .isEqualTo("jdbc:mysql://localhost/worknest?useSSL=false&zeroDateTimeBehavior=CONVERT_TO_NULL");
    }

    @Test
    void preservesAnExplicitZeroDatePolicy() {
        String url = "jdbc:mysql://localhost/worknest?zeroDateTimeBehavior=EXCEPTION";
        assertThat(MySqlDataSourceSupport.withSafeZeroDateHandling(url)).isEqualTo(url);
    }
}
