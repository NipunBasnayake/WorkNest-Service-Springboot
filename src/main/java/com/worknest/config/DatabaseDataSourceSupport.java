package com.worknest.config;

/**
 * Shared JDBC connection safeguards for master and tenant pools.
 */
public final class DatabaseDataSourceSupport {

    private DatabaseDataSourceSupport() {
    }

    public static boolean isPostgreSqlUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.regionMatches(true, 0, "jdbc:postgresql:", 0, "jdbc:postgresql:".length());
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
