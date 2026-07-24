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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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

    @TempDir
    Path temporaryDirectory;

    private final Map<Long, StoredFileMetadata> metadataById = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong(100);

    private LocalStorageProvider storageProvider;
    private StoredFileMetadataRepository metadataRepository;
    private FileStorageService service;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setRoot(temporaryDirectory.resolve("storage").toString());
        storageProvider = new LocalStorageProvider(properties);

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

        assertThat(storageProvider.listObjects("acme", "recruitment/resumes")).isEmpty();
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
        Path physicalFile = storageProvider.localTenantRoot()
                .resolve("acme")
                .resolve(metadata.getRelativePath());
        Files.writeString(physicalFile, "%PDF-1.7\nchanged\n%%EOF\n");

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
}
