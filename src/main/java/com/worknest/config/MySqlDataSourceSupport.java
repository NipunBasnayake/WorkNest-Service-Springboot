package com.worknest.config;

import java.util.Locale;

/**
 * Central MySQL connection safeguards shared by master and tenant pools.
 */
public final class MySqlDataSourceSupport {

    public static final String STRICT_DATE_CONNECTION_INIT_SQL =
            "SET SESSION sql_mode = CONCAT_WS(',', @@sql_mode, 'NO_ZERO_DATE', 'NO_ZERO_IN_DATE')";

    private MySqlDataSourceSupport() {
    }

    public static String withSafeZeroDateHandling(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return jdbcUrl;
        }
        if (jdbcUrl.toLowerCase(Locale.ROOT).contains("zerodatetimebehavior=")) {
            return jdbcUrl;
        }
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?")
                + "zeroDateTimeBehavior=CONVERT_TO_NULL";
    }
}
