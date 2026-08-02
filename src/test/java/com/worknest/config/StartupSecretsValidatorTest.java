package com.worknest.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupSecretsValidatorTest {

    private static final String DEFAULT_JWT_SECRET =
            "V29ya05lc3RTdXBlclNlY3JldEtleUZvckpXVDEyMzQ1Njc4OTAxMjM0NTY3ODkw";

    @Test
    void productionRejectsRootDatabaseAccount() {
        StartupSecretsValidator validator = validator(
                environment("prod"), "root", "strong-db-password", "strong-jwt-secret", false, "");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be root");
    }

    @Test
    void productionRejectsPostgresSuperuserAccount() {
        StartupSecretsValidator validator = validator(
                environment("prod"), "postgres", "strong-db-password", "strong-jwt-secret", false, "");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("postgres superuser");
    }

    @Test
    void productionRejectsKnownJwtDefault() {
        StartupSecretsValidator validator = validator(
                environment("prod"), "worknest_app", "strong-db-password", DEFAULT_JWT_SECRET, false, "");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret");
    }

    @Test
    void productionRejectsKnownBootstrapPassword() {
        StartupSecretsValidator validator = validator(
                environment("prod"), "worknest_app", "strong-db-password", "strong-jwt-secret", true, "ChangeMe123!");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bootstrap.platform-admin.password");
    }

    @Test
    void productionRequiresSupabaseServiceRoleKey() {
        StartupSecretsValidator validator = validator(
                environment("prod"), "worknest_app", "strong-db-password", "strong-jwt-secret", false, "", "");

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("storage.supabase.service-role-key");
    }

    @Test
    void developmentProfileDoesNotEnforceProductionSecrets() {
        StartupSecretsValidator validator = validator(
                environment("dev"), "root", "1234", DEFAULT_JWT_SECRET, true, "ChangeMe123!");

        assertThatCode(validator::run).doesNotThrowAnyException();
    }

    private StartupSecretsValidator validator(
            MockEnvironment environment,
            String databaseUser,
            String databasePassword,
            String jwtSecret,
            boolean bootstrapEnabled,
            String bootstrapPassword) {
        return new StartupSecretsValidator(
                environment,
                "jdbc:postgresql://worknest-postgres:5432/platform_master",
                databaseUser,
                databasePassword,
                jwtSecret,
                "",
                "",
                "",
                "https://app.example.com,http://localhost:5173",
                "https://app.example.com,http://localhost:5173",
                "https://app.example.com",
                "https://app.example.com/reset-password",
                "https://project-ref.supabase.co",
                "strong-supabase-service-role-key",
                "worknest-avatars",
                "worknest-documents",
                "worknest-recruitment",
                "worknest-chat",
                "worknest-projects",
                "worknest-logos",
                bootstrapEnabled,
                bootstrapPassword);
    }

    private StartupSecretsValidator validator(
            MockEnvironment environment,
            String databaseUser,
            String databasePassword,
            String jwtSecret,
            boolean bootstrapEnabled,
            String bootstrapPassword,
            String supabaseServiceRoleKey) {
        return new StartupSecretsValidator(
                environment,
                "jdbc:postgresql://worknest-postgres:5432/platform_master",
                databaseUser,
                databasePassword,
                jwtSecret,
                "",
                "",
                "",
                "https://app.example.com,http://localhost:5173",
                "https://app.example.com,http://localhost:5173",
                "https://app.example.com",
                "https://app.example.com/reset-password",
                "https://project-ref.supabase.co",
                supabaseServiceRoleKey,
                "worknest-avatars",
                "worknest-documents",
                "worknest-recruitment",
                "worknest-chat",
                "worknest-projects",
                "worknest-logos",
                bootstrapEnabled,
                bootstrapPassword);
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
