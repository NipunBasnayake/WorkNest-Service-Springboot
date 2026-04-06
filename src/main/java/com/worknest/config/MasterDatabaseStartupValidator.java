package com.worknest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MasterDatabaseStartupValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MasterDatabaseStartupValidator.class);

    private final JdbcTemplate masterJdbcTemplate;
    private final String dbUrl;
    private final String dbUsername;

    public MasterDatabaseStartupValidator(
            JdbcTemplate masterJdbcTemplate,
            @Value("${spring.datasource.url:}") String dbUrl,
            @Value("${spring.datasource.username:}") String dbUsername) {
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
    }

    @Override
    public void run(String... args) {
        try {
            Integer probe = masterJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (probe == null || probe != 1) {
                throw new IllegalStateException("Master DB probe returned unexpected response");
            }
            log.info("Master DB connectivity check passed for url='{}' user='{}'", sanitizeUrl(dbUrl), dbUsername);
        } catch (Exception ex) {
            Throwable root = rootCause(ex);
            String detail = root == null ? ex.getMessage() : root.getMessage();
            log.error("Master DB connectivity check failed. Verify DB_URL/DB_USERNAME/DB_PASSWORD and MySQL access. url='{}' user='{}' detail='{}'",
                    sanitizeUrl(dbUrl), dbUsername, detail);
            throw new IllegalStateException("Master database connection failed during startup. Check datasource credentials and access.", ex);
        }
    }

    private Throwable rootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String sanitizeUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "<unset>";
        }
        int queryIndex = jdbcUrl.indexOf('?');
        return queryIndex >= 0 ? jdbcUrl.substring(0, queryIndex) : jdbcUrl;
    }
}


