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
            "secret",
            "1234",
            "changeme123!",
            "v29ya05lc3rtdxblclnly3jldetleuzvckpxvdeymzq1njc4otaxmjm0nty3odkw"
    );

    private final Environment environment;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final String jwtSecret;
    private final String mailHost;
    private final String mailUsername;
    private final String mailPassword;
    private final String allowedOrigins;
    private final String websocketAllowedOrigins;
    private final String publicWebBaseUrl;
    private final String passwordResetLinkBaseUrl;
    private final String supabaseUrl;
    private final String supabaseServiceRoleKey;
    private final String supabaseBucketAvatars;
    private final String supabaseBucketDocuments;
    private final String supabaseBucketRecruitment;
    private final String supabaseBucketChat;
    private final String supabaseBucketProjects;
    private final String supabaseBucketLogos;
    private final boolean platformAdminBootstrapEnabled;
    private final String platformAdminBootstrapPassword;

    public StartupSecretsValidator(
            Environment environment,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${app.cors.allowed-origins:}") String allowedOrigins,
            @Value("${app.websocket.allowed-origins:}") String websocketAllowedOrigins,
            @Value("${app.public-web-base-url:}") String publicWebBaseUrl,
            @Value("${app.auth.password-reset.link-base-url:}") String passwordResetLinkBaseUrl,
            @Value("${storage.supabase.url:}") String supabaseUrl,
            @Value("${storage.supabase.service-role-key:}") String supabaseServiceRoleKey,
            @Value("${storage.supabase.buckets.avatars:}") String supabaseBucketAvatars,
            @Value("${storage.supabase.buckets.documents:}") String supabaseBucketDocuments,
            @Value("${storage.supabase.buckets.recruitment:}") String supabaseBucketRecruitment,
            @Value("${storage.supabase.buckets.chat:}") String supabaseBucketChat,
            @Value("${storage.supabase.buckets.projects:}") String supabaseBucketProjects,
            @Value("${storage.supabase.buckets.logos:}") String supabaseBucketLogos,
            @Value("${bootstrap.platform-admin.enabled:false}") boolean platformAdminBootstrapEnabled,
            @Value("${bootstrap.platform-admin.password:}") String platformAdminBootstrapPassword) {
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.jwtSecret = jwtSecret;
        this.mailHost = mailHost;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.allowedOrigins = allowedOrigins;
        this.websocketAllowedOrigins = websocketAllowedOrigins;
        this.publicWebBaseUrl = publicWebBaseUrl;
        this.passwordResetLinkBaseUrl = passwordResetLinkBaseUrl;
        this.supabaseUrl = supabaseUrl;
        this.supabaseServiceRoleKey = supabaseServiceRoleKey;
        this.supabaseBucketAvatars = supabaseBucketAvatars;
        this.supabaseBucketDocuments = supabaseBucketDocuments;
        this.supabaseBucketRecruitment = supabaseBucketRecruitment;
        this.supabaseBucketChat = supabaseBucketChat;
        this.supabaseBucketProjects = supabaseBucketProjects;
        this.supabaseBucketLogos = supabaseBucketLogos;
        this.platformAdminBootstrapEnabled = platformAdminBootstrapEnabled;
        this.platformAdminBootstrapPassword = platformAdminBootstrapPassword;
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

        if ("root".equalsIgnoreCase(datasourceUsername.trim())) {
            throw new IllegalStateException("Production database user must not be root");
        }
        if ("postgres".equalsIgnoreCase(datasourceUsername.trim())) {
            throw new IllegalStateException("Production database user must not be postgres superuser");
        }

        if (!isBlank(mailHost) || !isBlank(mailUsername)) {
            requireNonBlank(mailHost, "spring.mail.host");
            requireNonBlank(mailUsername, "spring.mail.username");
            requireNonBlank(mailPassword, "spring.mail.password");
        }

        rejectWeakPlaceholder(jwtSecret, "app.jwt.secret");
        rejectWeakPlaceholder(datasourcePassword, "spring.datasource.password");
        validateOrigins(allowedOrigins, "app.cors.allowed-origins");
        validateOrigins(websocketAllowedOrigins, "app.websocket.allowed-origins");
        requireProductionUrl(publicWebBaseUrl, "app.public-web-base-url");
        requireProductionUrl(passwordResetLinkBaseUrl, "app.auth.password-reset.link-base-url");
        requireProductionUrl(supabaseUrl, "storage.supabase.url");
        requireNonBlank(supabaseServiceRoleKey, "storage.supabase.service-role-key");
        rejectWeakPlaceholder(supabaseServiceRoleKey, "storage.supabase.service-role-key");
        requireNonBlank(supabaseBucketAvatars, "storage.supabase.buckets.avatars");
        requireNonBlank(supabaseBucketDocuments, "storage.supabase.buckets.documents");
        requireNonBlank(supabaseBucketRecruitment, "storage.supabase.buckets.recruitment");
        requireNonBlank(supabaseBucketChat, "storage.supabase.buckets.chat");
        requireNonBlank(supabaseBucketProjects, "storage.supabase.buckets.projects");
        requireNonBlank(supabaseBucketLogos, "storage.supabase.buckets.logos");
        if (!isBlank(mailPassword)) {
            rejectWeakPlaceholder(mailPassword, "spring.mail.password");
        }
        if (platformAdminBootstrapEnabled) {
            requireNonBlank(platformAdminBootstrapPassword, "bootstrap.platform-admin.password");
            rejectWeakPlaceholder(platformAdminBootstrapPassword, "bootstrap.platform-admin.password");
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

    private void validateOrigins(String value, String propertyName) {
        requireNonBlank(value, propertyName);
        String[] origins = value.split(",");
        boolean hasProductionOrigin = false;
        for (String origin : origins) {
            String normalized = trimToEmpty(origin).toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if ("*".equals(normalized) || normalized.contains("*")) {
                throw new IllegalStateException("Wildcard origin is not allowed for production property: " + propertyName);
            }
            if (!isLocalUrl(normalized)) {
                hasProductionOrigin = true;
            }
        }
        if (!hasProductionOrigin) {
            throw new IllegalStateException("At least one non-local production origin is required for: " + propertyName);
        }
    }

    private void requireProductionUrl(String value, String propertyName) {
        requireNonBlank(value, propertyName);
        String normalized = trimToEmpty(value).toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("https://")) {
            throw new IllegalStateException("Production URL must use https for property: " + propertyName);
        }
        if (isLocalUrl(normalized)) {
            throw new IllegalStateException("Production URL must not point to localhost for property: " + propertyName);
        }
    }

    private boolean isLocalUrl(String value) {
        return value.contains("localhost")
                || value.contains("127.0.0.1")
                || value.contains("0.0.0.0")
                || value.contains("[::1]");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
