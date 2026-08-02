package com.worknest.common.storage;

import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.entity.StoredFileMetadata;
import com.worknest.tenant.repository.StoredFileMetadataRepository;
import com.worknest.tenant.repository.StoredFileVariantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    private final Map<Long, StoredFileMetadata> metadataById = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong(100);

    private InMemoryStorageProvider storageProvider;
    private StoredFileMetadataRepository metadataRepository;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        storageProvider = new InMemoryStorageProvider();

        metadataRepository = mock(StoredFileMetadataRepository.class);
        when(metadataRepository.saveAndFlush(any(StoredFileMetadata.class))).thenAnswer(invocation -> {
            StoredFileMetadata metadata = invocation.getArgument(0);
            if (metadata.getId() == null) {
                metadata.setId(ids.incrementAndGet());
            }
            metadataById.put(metadata.getId(), metadata);
            return metadata;
        });
        when(metadataRepository.save(any(StoredFileMetadata.class))).thenAnswer(invocation -> {
            StoredFileMetadata metadata = invocation.getArgument(0);
            if (metadata.getId() == null) {
                metadata.setId(ids.incrementAndGet());
            }
            metadataById.put(metadata.getId(), metadata);
            return metadata;
        });
        when(metadataRepository.findById(any(Long.class)))
                .thenAnswer(invocation -> Optional.ofNullable(metadataById.get(invocation.getArgument(0))));

        MasterTenantLookupService tenantLookupService = mock(MasterTenantLookupService.class);
        PlatformTenant tenant = new PlatformTenant();
        tenant.setTenantKey("tenant-acme");
        tenant.setSlug("acme");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setActive(true);
        when(tenantLookupService.findBySlug("acme")).thenReturn(Optional.of(tenant));

        service = new FileStorageService(
                properties,
                storageProvider,
                metadataRepository,
                mock(StoredFileAccessPolicy.class),
                mock(SecurityUtils.class),
                tenantLookupService,
                mock(ImageAssetProcessor.class),
                mock(StoredFileVariantRepository.class),
                mock(AssetObservability.class));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void storesPdfBytesAndMatchingMetadataThenDownloadsTheSameFile() throws Exception {
        byte[] pdf = validPdf();

        StoredFileDto stored = service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.pdf", "application/pdf", pdf));

        StoredFileMetadata metadata = metadataById.get(Long.valueOf(stored.id()));
        assertThat(metadata).isNotNull();
        assertThat(metadata.getOriginalFilename()).isEqualTo("resume.pdf");
        assertThat(metadata.getContentType()).isEqualTo("application/pdf");
        assertThat(metadata.getFileSize()).isEqualTo(pdf.length);
        assertThat(metadata.getSha256()).isEqualTo(sha256(pdf));
        assertThat(metadata.getStorageCategory()).isEqualTo(StorageCategory.CANDIDATE_RESUME);
        assertThat(metadata.getRelatedModule()).isEqualTo(StorageCategory.CANDIDATE_RESUME.name());
        assertThat(metadata.getLifecycleState()).isEqualTo("ACTIVE");
        assertThat(metadata.isActive()).isTrue();
        assertThat(storageProvider.exists("acme", metadata.getRelativePath())).isTrue();
        assertThat(service.download("acme", "wnfileid://" + stored.id()).resource().getContentAsByteArray())
                .isEqualTo(pdf);
    }

    @Test
    void storesAndDownloadsAValidDocx() throws Exception {
        byte[] docx = validDocx();

        StoredFileDto stored = service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload(
                        "resume.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        docx));

        StoredFileMetadata metadata = metadataById.get(Long.valueOf(stored.id()));
        assertThat(metadata.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(metadata.getSha256()).isEqualTo(sha256(docx));
        assertThat(service.download("acme", "wnfileid://" + stored.id()).resource().getContentAsByteArray())
                .isEqualTo(docx);
    }

    @Test
    void removesPhysicalFileWhenOwningTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        StoredFileDto stored = service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.pdf", "application/pdf", validPdf()));
        StoredFileMetadata metadata = metadataById.get(Long.valueOf(stored.id()));
        assertThat(storageProvider.exists("acme", metadata.getRelativePath())).isTrue();

        completeSynchronization(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(storageProvider.exists("acme", metadata.getRelativePath())).isFalse();
    }

    @Test
    void removesPhysicalFileWhenMetadataPersistenceFails() {
        when(metadataRepository.saveAndFlush(any(StoredFileMetadata.class)))
                .thenThrow(new IllegalStateException("database write failed"));

        assertThatThrownBy(() -> service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.pdf", "application/pdf", validPdf())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database write failed");

        assertThat(storageProvider.listObjects("acme", "candidate-cvs")).isEmpty();
    }

    @Test
    void deletesPhysicalFileOnlyAfterMetadataTransactionCommits() {
        StoredFileDto stored = service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.pdf", "application/pdf", validPdf()));
        StoredFileMetadata metadata = metadataById.get(Long.valueOf(stored.id()));

        TransactionSynchronizationManager.initSynchronization();
        service.delete("acme", "wnfileid://" + stored.id());

        assertThat(metadata.isActive()).isFalse();
        assertThat(metadata.getLifecycleState()).isEqualTo("DELETED");
        assertThat(storageProvider.exists("acme", metadata.getRelativePath())).isTrue();

        completeSynchronization(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(storageProvider.exists("acme", metadata.getRelativePath())).isFalse();
    }

    @Test
    void keepsPhysicalFileWhenDeleteTransactionRollsBack() {
        StoredFileDto stored = service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.pdf", "application/pdf", validPdf()));
        StoredFileMetadata metadata = metadataById.get(Long.valueOf(stored.id()));

        TransactionSynchronizationManager.initSynchronization();
        service.delete("acme", "wnfileid://" + stored.id());
        completeSynchronization(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(storageProvider.exists("acme", metadata.getRelativePath())).isTrue();
    }

    @Test
    void rejectsMissingUnsupportedOversizedAndCorruptResumesBeforeMetadataIsSaved() {
        assertThatThrownBy(() -> service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("File is required");

        assertThatThrownBy(() -> service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47})))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only PDF and DOCX resumes are allowed");

        assertThatThrownBy(() -> service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.pdf", "application/pdf", "%PDF-1.7\ntruncated".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Uploaded PDF document is incomplete or corrupt");

        byte[] oversized = new byte[(10 * 1024 * 1024) + 1];
        byte[] header = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, oversized, 0, header.length);
        assertThatThrownBy(() -> service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.pdf", "application/pdf", oversized)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("File size exceeds 10MB limit");

        verify(metadataRepository, never()).saveAndFlush(any(StoredFileMetadata.class));
    }

    @Test
    void detectsAStoredFileThatWasChangedAfterUpload() throws Exception {
        StoredFileDto stored = service.store(
                "acme",
                StorageCategory.CANDIDATE_RESUME,
                upload("resume.pdf", "application/pdf", validPdf()));
        StoredFileMetadata metadata = metadataById.get(Long.valueOf(stored.id()));
        storageProvider.corrupt("acme", metadata.getRelativePath(), "%PDF-1.7\nchanged\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.download("acme", "wnfileid://" + stored.id()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("File not found");
    }

    private void completeSynchronization(int status) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
    }

    private MockMultipartFile upload(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("resume", filename, contentType, content);
    }

    private byte[] validPdf() {
        return "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n"
                .getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] validDocx() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static class InMemoryStorageProvider implements StorageProvider {
        private final Map<String, StoredObject> objects = new LinkedHashMap<>();

        @Override
        public void initialize(java.util.Collection<List<String>> categoryDirectories) {
        }

        @Override
        public void initializeTenant(String tenantSlug, java.util.Collection<List<String>> categoryDirectories) {
        }

        @Override
        public String bucketForPath(String relativePath) {
            String root = relativePath.split("/", 2)[0];
            return switch (root) {
                case "avatars" -> "avatars";
                case "candidate-cvs" -> "recruitment";
                case "chat-files" -> "chat";
                case "project-files" -> "projects";
                case "company-logos" -> "logos";
                default -> "documents";
            };
        }

        @Override
        public String objectKey(String tenantSlug, String relativePath) {
            String[] segments = relativePath.split("/", 2);
            return segments.length == 1
                    ? segments[0] + "/" + tenantSlug
                    : segments[0] + "/" + tenantSlug + "/" + segments[1];
        }

        @Override
        public void write(String tenantSlug, String relativePath, byte[] content) {
            objects.put(key(tenantSlug, relativePath), new StoredObject(Arrays.copyOf(content, content.length), Instant.now()));
        }

        @Override
        public Resource read(String tenantSlug, String relativePath) {
            StoredObject object = objects.get(key(tenantSlug, relativePath));
            if (object == null) throw new ResourceNotFoundException("File not found");
            return new ByteArrayResource(Arrays.copyOf(object.bytes(), object.bytes().length));
        }

        @Override
        public boolean exists(String tenantSlug, String relativePath) {
            return objects.containsKey(key(tenantSlug, relativePath));
        }

        @Override
        public boolean hashMatches(String tenantSlug, String relativePath, String expectedSha256) {
            StoredObject object = objects.get(key(tenantSlug, relativePath));
            if (object == null) return false;
            try {
                return MessageDigest.isEqual(
                        HexFormat.of().parseHex(expectedSha256),
                        MessageDigest.getInstance("SHA-256").digest(object.bytes()));
            } catch (Exception exception) {
                return false;
            }
        }

        @Override
        public void delete(String tenantSlug, String relativePath) {
            objects.remove(key(tenantSlug, relativePath));
        }

        @Override
        public String getPublicUrl(String tenantSlug, String relativePath) {
            return "https://storage.example.test/" + objectKey(tenantSlug, relativePath);
        }

        @Override
        public String getSignedUrl(String tenantSlug, String relativePath, Duration expiresIn) {
            return getPublicUrl(tenantSlug, relativePath) + "?signed=true";
        }

        @Override
        public void move(String tenantSlug, String sourceRelativePath, String destinationRelativePath) {
            copy(tenantSlug, sourceRelativePath, destinationRelativePath);
            delete(tenantSlug, sourceRelativePath);
        }

        @Override
        public void copy(String tenantSlug, String sourceRelativePath, String destinationRelativePath) {
            StoredObject object = objects.get(key(tenantSlug, sourceRelativePath));
            if (object == null) throw new ResourceNotFoundException("File not found");
            objects.put(key(tenantSlug, destinationRelativePath), new StoredObject(Arrays.copyOf(object.bytes(), object.bytes().length), Instant.now()));
        }

        @Override
        public List<StoredObjectDescriptor> listObjects(String tenantSlug, String relativePrefix) {
            List<StoredObjectDescriptor> result = new ArrayList<>();
            String prefix = tenantSlug + "/" + relativePrefix;
            objects.forEach((path, object) -> {
                if (path.startsWith(prefix)) {
                    result.add(new StoredObjectDescriptor(path.substring(tenantSlug.length() + 1), object.bytes().length, object.updatedAt()));
                }
            });
            return result;
        }

        void corrupt(String tenantSlug, String relativePath, byte[] content) {
            write(tenantSlug, relativePath, content);
        }

        private String key(String tenantSlug, String relativePath) {
            return tenantSlug + "/" + relativePath;
        }

        private record StoredObject(byte[] bytes, Instant updatedAt) {
        }
    }
}
