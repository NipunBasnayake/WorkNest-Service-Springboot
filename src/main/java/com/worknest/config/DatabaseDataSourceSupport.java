package com.worknest.config;

/**
 * Shared JDBC connection safeguards for master and tenant pools.
 */
public final class DatabaseDataSourceSupport {

    private DatabaseDataSourceSupport() {
    }

    public static boolean isMySqlUrl(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.regionMatches(true, 0, "jdbc:mysql:", 0, "jdbc:mysql:".length());
    }

    public static void requireMySqlUrl(String jdbcUrl) {
        if (!isMySqlUrl(jdbcUrl)) {
            throw new IllegalStateException("WorkNest requires a MySQL JDBC URL");
        }
    }

    public static String sanitizeUrl(String jdbcUrl) {
        String value = trimToNull(jdbcUrl);
        if (value == null) {
            return "<unset>";
        }
        int queryIndex = value.indexOf('?');
        return queryIndex >= 0 ? value.substring(0, queryIndex) : value;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
