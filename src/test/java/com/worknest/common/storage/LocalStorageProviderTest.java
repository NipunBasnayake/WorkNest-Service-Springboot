package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageProviderTest {
    @TempDir
    Path temporaryDirectory;

    private LocalStorageProvider provider;
    private final List<List<String>> categories = List.of(
            List.of("employees", "photos"),
            List.of("recruitment", "resumes"),
            List.of("tasks", "attachments"));

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(temporaryDirectory.resolve("storage").toString());
        provider = new LocalStorageProvider(properties);
        provider.initialize(categories);
        provider.initializeTenant("acme", categories);
    }

    @Test
    void createsOrganizedTenantDirectoriesAndRoundTripsAFile() throws Exception {
        provider.write("acme", "recruitment/resumes/resume.pdf", "%PDF-test".getBytes(StandardCharsets.UTF_8));

        assertThat(temporaryDirectory.resolve("storage/tenants/acme/employees/photos")).isDirectory();
        assertThat(provider.exists("acme", "recruitment/resumes/resume.pdf")).isTrue();
        assertThat(provider.read("acme", "recruitment/resumes/resume.pdf").getContentAsByteArray())
                .isEqualTo("%PDF-test".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsPathTraversalOutsideTheTenantRoot() {
        assertThatThrownBy(() -> provider.write("acme", "../../outside.txt", new byte[]{1}))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid storage path");
    }

    @Test
    void keepsIdenticallyNamedFilesIsolatedByTenant() throws Exception {
        provider.initializeTenant("globex", categories);
        provider.write("acme", "tasks/attachments/brief.pdf", "acme".getBytes(StandardCharsets.UTF_8));
        provider.write("globex", "tasks/attachments/brief.pdf", "globex".getBytes(StandardCharsets.UTF_8));

        assertThat(provider.read("acme", "tasks/attachments/brief.pdf").getContentAsByteArray())
                .isEqualTo("acme".getBytes(StandardCharsets.UTF_8));
        assertThat(provider.read("globex", "tasks/attachments/brief.pdf").getContentAsByteArray())
                .isEqualTo("globex".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.readString(temporaryDirectory.resolve("storage/tenants/acme/tasks/attachments/brief.pdf")))
                .isEqualTo("acme");
    }

    @Test
    void verifiesStoredObjectIntegrityWithoutCrossTenantReads() throws Exception {
        byte[] content = "trusted-avatar".getBytes(StandardCharsets.UTF_8);
        provider.write("acme", "employees/photos/avatar.png", content);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

        assertThat(provider.hashMatches("acme", "employees/photos/avatar.png", sha256)).isTrue();
        assertThat(provider.hashMatches("acme", "employees/photos/avatar.png", "0".repeat(64))).isFalse();
        assertThat(provider.hashMatches("acme", "employees/photos/missing.png", sha256)).isFalse();
    }

    @Test
    void listsOnlyObjectsInsideRequestedTenantPrefix() {
        provider.write("acme", "employees/photos/a/original.png", new byte[] {1, 2, 3});
        provider.write("acme", "employees/photos/1/avatar.png", new byte[] {4});
        provider.initializeTenant("other", categories);
        provider.write("other", "employees/photos/b/original.png", new byte[] {5});

        List<StoredObjectDescriptor> objects = provider.listObjects("acme", "employees/photos/a");

        assertThat(objects).singleElement().satisfies(object -> {
            assertThat(object.relativePath()).isEqualTo("employees/photos/a/original.png");
            assertThat(object.size()).isEqualTo(3);
            assertThat(object.lastModified()).isNotNull();
        });
    }
}
