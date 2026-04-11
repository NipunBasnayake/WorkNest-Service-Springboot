package com.worknest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
@Order(5)
public class StartupSecretsValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSecretsValidator.class);
    private static final Set<String> WEAK_PLACEHOLDERS = Set.of(
            "change-me",
            "changeme",
            "example",
            "replace-me",
            "password",
            "secret"
    );

    private final Environment environment;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final String jwtSecret;
    private final String mailHost;
    private final String mailUsername;
    private final String mailPassword;
    private final String ddlAuto;

    public StartupSecretsValidator(
            Environment environment,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${spring.jpa.hibernate.ddl-auto:}") String ddlAuto) {
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.jwtSecret = jwtSecret;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.ddlAuto = ddlAuto;
    }

    @Override
    public void run(String... args) {
        if (!isProductionProfile()) {
            return;
        }

        requireNonBlank(datasourceUrl, "spring.datasource.url");
        requireNonBlank(datasourceUsername, "spring.datasource.username");
        requireNonBlank(datasourcePassword, "spring.datasource.password");
        requireNonBlank(jwtSecret, "app.jwt.secret");

        if (!isBlank(mailHost) || !isBlank(mailUsername)) {
            requireNonBlank(mailHost, "spring.mail.host");
            requireNonBlank(mailUsername, "spring.mail.username");
            requireNonBlank(mailPassword, "spring.mail.password");
        }

        if ("update".equalsIgnoreCase(trimToEmpty(ddlAuto))) {
            throw new IllegalStateException("spring.jpa.hibernate.ddl-auto=update is not allowed in production profile");
        }

        rejectWeakPlaceholder(jwtSecret, "app.jwt.secret");
        rejectWeakPlaceholder(datasourcePassword, "spring.datasource.password");
        if (!isBlank(mailPassword)) {
            rejectWeakPlaceholder(mailPassword, "spring.mail.password");
        }

        log.info("Startup production configuration validation passed");
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
    }

    private void requireNonBlank(String value, String propertyName) {
        if (isBlank(value)) {
            throw new IllegalStateException("Missing required production property: " + propertyName);
        }
    }

    private void rejectWeakPlaceholder(String value, String propertyName) {
        String normalized = trimToEmpty(value).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return;
        }
        if (WEAK_PLACEHOLDERS.contains(normalized)) {
            throw new IllegalStateException(
                    "Insecure placeholder value detected for production property: " + propertyName
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

