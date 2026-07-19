package com.worknest.common.storage;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.multitenancy.context.TenantContextHolder;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.entity.StoredFileMetadata;
import com.worknest.tenant.entity.StoredFileVariant;
import com.worknest.tenant.repository.StoredFileMetadataRepository;
import com.worknest.tenant.repository.StoredFileVariantRepository;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileStorageService {

    private static final String INTERNAL_PREFIX = "wnfile://";
    private static final String INTERNAL_ID_PREFIX = "wnfileid://";
    private static final String STORAGE_BUCKET = "worknest-local";
    private static final Set<String> ALLOWED_ROOT_DIRECTORIES = Set.of(
            "companies", "workspace", "employees", "recruitment", "projects", "tasks", "announcements",
            "leave", "chat", "documents", "temporary", "images", "docs"
    );
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "ade", "adp", "apk", "app", "bat", "bin", "cmd", "com", "cpl", "dll", "dmg", "elf",
            "exe", "gadget", "hta", "ins", "iso", "jar", "js", "jse", "lib", "lnk", "msi", "msp",
            "mst", "pif", "ps1", "scr", "sh", "sys", "vb", "vbe", "vbs", "war", "ws", "wsf"
    );

    private final StorageProperties storageProperties;
    private final StorageProvider storageProvider;
    private final StoredFileMetadataRepository storedFileMetadataRepository;
    private final StoredFileAccessPolicy storedFileAccessPolicy;
    private final SecurityUtils securityUtils;
    private final MasterTenantLookupService masterTenantLookupService;
    private final ImageAssetProcessor imageAssetProcessor;
    private final StoredFileVariantRepository storedFileVariantRepository;
    private final AssetObservability observability;

    public record StoredFileResult(
            String url,
            String path,
            String name,
            String mimeType,
            long size,
            String bucket,
            String uploadedAt) {
    }

    public record StoredFileResource(Resource resource, String fileName, String mimeType, String etag) {
    }

    private record ValidatedFile(
            String id,
            String originalName,
            String extension,
            String storedFileName,
            String contentType,
            byte[] bytes,
            long size) {
    }

    private enum FileKind {
        IMAGE,
        DOCUMENT
    }

    public FileStorageService(
            StorageProperties storageProperties,
            StorageProvider storageProvider,
            StoredFileMetadataRepository storedFileMetadataRepository,
            StoredFileAccessPolicy storedFileAccessPolicy,
            SecurityUtils securityUtils,
            MasterTenantLookupService masterTenantLookupService,
            ImageAssetProcessor imageAssetProcessor,
            StoredFileVariantRepository storedFileVariantRepository,
            AssetObservability observability) {
        this.storageProperties = storageProperties;
        this.storageProvider = storageProvider;
        this.storedFileMetadataRepository = storedFileMetadataRepository;
        this.storedFileAccessPolicy = storedFileAccessPolicy;
        this.securityUtils = securityUtils;
        this.masterTenantLookupService = masterTenantLookupService;
        this.imageAssetProcessor = imageAssetProcessor;
        this.storedFileVariantRepository = storedFileVariantRepository;
        this.observability = observability;
        createDirectories();
    }

    public Path getTenantsRootPath() {
        return storageProvider.localTenantRoot();
    }

    public StoredFileResult storeForUpload(MultipartFile file, StorageCategory category) {
        return toUploadResult(store(file, category));
    }

    public StoredFileDto store(MultipartFile file, StorageCategory category, String... pathSegments) {
        return store(resolveCurrentTenantSlug(), category, file, pathSegments);
    }

    public StoredFileDto store(String tenantSlug, StorageCategory category, MultipartFile file, String... pathSegments) {
        StorageCategory resolvedCategory = requireCategory(category);
        ValidatedFile validatedFile = validateFile(file, resolvedCategory);
        String normalizedTenantSlug = requireActiveTenantSlug(tenantSlug);
        createDirectories(normalizedTenantSlug);

        List<String> relativeSegments = new ArrayList<>(resolvedCategory.directorySegments());
        relativeSegments.addAll(normalizePathSegments(pathSegments));
        relativeSegments.add(validatedFile.storedFileName());
        String storedName = String.join("/", relativeSegments);
        storageProvider.write(normalizedTenantSlug, storedName, validatedFile.bytes());

        try {
            StoredFileMetadata metadata = new StoredFileMetadata();
            metadata.setOriginalFilename(validatedFile.originalName());
            metadata.setStoredFilename(validatedFile.storedFileName());
            metadata.setRelativePath(storedName);
            metadata.setExtension(validatedFile.extension());
            metadata.setContentType(validatedFile.contentType());
            metadata.setFileSize(validatedFile.size());
            metadata.setUploadedByUserId(currentUserIdOrNull());
            metadata.setStorageCategory(resolvedCategory);
            metadata.setRelatedModule(resolvedCategory.name());
            StoredFileMetadata saved = storedFileMetadataRepository.save(metadata);
            return toDto(normalizedTenantSlug, saved);
        } catch (RuntimeException exception) {
            storageProvider.delete(normalizedTenantSlug, storedName);
            throw exception;
        }
    }

    public StoredFileDto replace(MultipartFile file, StorageCategory category, String existingStoredName, String... pathSegments) {
        String tenantSlug = resolveCurrentTenantSlug();
        return replace(tenantSlug, category, existingStoredName, file, pathSegments);
    }

    public StoredFileDto replace(String tenantSlug, StorageCategory category, String existingStoredName, MultipartFile file, String... pathSegments) {
        StoredFileDto storedFile = store(tenantSlug, category, file, pathSegments);
        if (trimToNull(existingStoredName) != null) {
            delete(tenantSlug, existingStoredName);
        }
        return storedFile;
    }

    public void delete(String storedName) {
        delete(resolveCurrentTenantSlug(), storedName);
    }

    public void delete(String tenantSlug, String storedName) {
        String normalizedTenantSlug = requireActiveTenantSlug(tenantSlug);
        Long metadataId = parseMetadataId(storedName);
        if (metadataId != null) {
            StoredFileMetadata metadata = getMetadata(metadataId);
            storageProvider.delete(normalizedTenantSlug, metadata.getRelativePath());
            metadata.setActive(false);
            storedFileMetadataRepository.save(metadata);
            return;
        }
        String relativePath = normalizeStoredName(storedName);
        storageProvider.delete(normalizedTenantSlug, relativePath);
        storedFileMetadataRepository.findByRelativePathAndActiveTrue(relativePath).ifPresent(metadata -> {
            metadata.setActive(false);
            storedFileMetadataRepository.save(metadata);
        });
    }

    public boolean exists(String storedName) {
        return exists(resolveCurrentTenantSlug(), storedName);
    }

    public boolean exists(String tenantSlug, String storedName) {
        String normalizedTenantSlug = requireActiveTenantSlug(tenantSlug);
        Long metadataId = parseMetadataId(storedName);
        if (metadataId != null) {
            StoredFileMetadata metadata = getMetadata(metadataId);
            return storageProvider.exists(normalizedTenantSlug, metadata.getRelativePath());
        }
        String relativePath = normalizeStoredName(storedName);
        return storageProvider.exists(normalizedTenantSlug, relativePath);
    }

    public StoredFileResource download(String storedName) {
        return download(resolveCurrentTenantSlug(), storedName);
    }

    public StoredFileResource download(String tenantSlug, String storedName) {
        String normalizedTenantSlug = requireActiveTenantSlug(tenantSlug);
        Long metadataId = parseMetadataId(storedName);
        if (metadataId != null) return resource(normalizedTenantSlug, getMetadata(metadataId));
        String relativePath = normalizeStoredName(storedName);
        Resource resource = storageProvider.read(normalizedTenantSlug, relativePath);
        String fileName = fileNameFromStoredName(relativePath);
        return new StoredFileResource(resource, fileName, detectContentTypeFromName(fileName), null);
    }

    public String buildPublicUrl(String tenantSlug, String storedName) {
        String normalizedTenantSlug = normalizeTenantSlug(tenantSlug);
        Long metadataId = parseMetadataId(storedName);
        if (metadataId != null) return buildFileApiUrl(normalizedTenantSlug, metadataId, "preview");
        String relativePath = normalizeStoredName(storedName);
        return "/files/" + encodePathSegment(normalizedTenantSlug) + "/" + encodeRelativePath(relativePath);
    }

    public void validate(MultipartFile file, StorageCategory category) {
        validateFile(file, requireCategory(category));
    }

    public void validateUploadAccess(StorageCategory category) {
        StorageCategory resolvedCategory = requireCategory(category);
        if (resolvedCategory == StorageCategory.EMPLOYEE_AVATAR
                || resolvedCategory == StorageCategory.WORKSPACE_LOGO
                || resolvedCategory == StorageCategory.WORKSPACE_BANNER) {
            throw new BadRequestException("Identity and branding images must use their owning module endpoint");
        }
        storedFileAccessPolicy.requireUpload(resolvedCategory);
    }

    public void createDirectories() {
        storageProvider.initialize(java.util.Arrays.stream(StorageCategory.values()).map(StorageCategory::directorySegments).toList());
    }

    public void createDirectories(String tenantSlug) {
        String normalizedTenantSlug = normalizeTenantSlug(tenantSlug);
        storageProvider.initializeTenant(normalizedTenantSlug, java.util.Arrays.stream(StorageCategory.values()).map(StorageCategory::directorySegments).toList());
    }

    public void validateTenantFileAccess(String tenantSlug, String relativePath) {
        String normalizedTenantSlug = requireActiveTenantSlug(tenantSlug);
        PlatformUserPrincipal principal = securityUtils.getCurrentPrincipalOrThrow();
        PlatformRole role = principal.getRole();
        PlatformTenant tenant = findActiveTenantBySlug(normalizedTenantSlug);

        if (!role.isPlatformAdmin()) {
            String principalTenantKey = normalizeOptionalIdentifier(principal.getTenantKey());
            String tenantKey = normalizeOptionalIdentifier(tenant.getTenantKey());
            String tenantRecordSlug = normalizeOptionalIdentifier(tenant.getSlug());
            if (principalTenantKey == null || (!principalTenantKey.equals(tenantKey) && !principalTenantKey.equals(tenantRecordSlug))) {
                throw new ForbiddenOperationException("You are not allowed to access files for another tenant");
            }
        }

        String normalizedPath = normalizeRelativePath(relativePath);
        if (!storageProvider.exists(normalizedTenantSlug, normalizedPath)) {
            throw new ResourceNotFoundException("File not found");
        }
    }

    public String normalizeStoredReference(String fileUrl) {
        String normalized = trimToNull(fileUrl);
        if (normalized == null) {
            throw new BadRequestException("fileUrl is required");
        }
        Long metadataId = parseMetadataId(normalized);
        if (metadataId != null) {
            getMetadata(metadataId);
            return INTERNAL_ID_PREFIX + metadataId;
        }
        if (isLocalReference(normalized)) {
            return INTERNAL_PREFIX + normalizeRelativePath(normalized.substring(INTERNAL_PREFIX.length()));
        }
        String parsedPublicReference = parsePublicFileUrl(normalized);
        if (parsedPublicReference != null) {
            return INTERNAL_PREFIX + parsedPublicReference;
        }
        if (normalized.startsWith("/uploads/")) {
            return INTERNAL_PREFIX + normalizeRelativePath(normalized.substring("/uploads/".length()));
        }
        throw new BadRequestException("Only local WorkNest file references are supported");
    }

    public String toPublicUrl(String storedReference) {
        String normalized = trimToNull(storedReference);
        if (normalized == null) {
            return null;
        }
        if (!isLocalReference(normalized)) {
            return normalized;
        }
        Long metadataId = parseMetadataId(normalized);
        if (metadataId != null) return buildFileApiUrl(resolveCurrentTenantSlug(), metadataId, "preview");
        return buildPublicUrl(resolveCurrentTenantSlug(), normalized);
    }

    public boolean isLocalReference(String value) {
        return value != null && (value.startsWith(INTERNAL_PREFIX) || value.startsWith(INTERNAL_ID_PREFIX));
    }

    public StoredFileResource loadAsResource(String storedReference) {
        return download(resolveCurrentTenantSlug(), storedReference);
    }

    private StoredFileResult toUploadResult(StoredFileDto storedFile) {
        return new StoredFileResult(
                storedFile.url(),
                INTERNAL_ID_PREFIX + storedFile.id(),
                storedFile.originalName(),
                storedFile.contentType(),
                storedFile.size(),
                STORAGE_BUCKET,
                storedFile.uploadedAt().toString()
        );
    }

    public StoredFileDto getFile(Long id) {
        StoredFileMetadata metadata = getMetadata(id);
        storedFileAccessPolicy.requireRead(metadata);
        return toDto(resolveCurrentTenantSlug(), metadata);
    }

    public StoredFileResource getFileResource(Long id) {
        StoredFileMetadata metadata = getMetadata(id);
        storedFileAccessPolicy.requireRead(metadata);
        String tenantSlug = resolveCurrentTenantSlug();
        requireIntegrity(tenantSlug, metadata.getRelativePath(), metadata.getSha256());
        return resource(tenantSlug, metadata);
    }

    public StoredFileResource getFileVariantResource(Long id, String variantName) {
        StoredFileMetadata metadata = getMetadata(id);
        storedFileAccessPolicy.requireRead(metadata);
        String normalizedVariant = trimToNull(variantName);
        if (normalizedVariant == null || !normalizedVariant.matches("[0-9]{2,4}")) {
            throw new ResourceNotFoundException("File variant not found");
        }
        StoredFileVariant variant = storedFileVariantRepository
                .findBySourceFileIdAndVariantName(id, normalizedVariant)
                .orElseThrow(() -> new ResourceNotFoundException("File variant not found"));
        if (!storageProvider.exists(resolveCurrentTenantSlug(), variant.getRelativePath())) {
            observability.recordFallback("avatar_variant_missing");
            throw new ResourceNotFoundException("File variant not found");
        }
        String tenantSlug = resolveCurrentTenantSlug();
        requireIntegrity(tenantSlug, variant.getRelativePath(), variant.getSha256());
        return new StoredFileResource(
                storageProvider.read(tenantSlug, variant.getRelativePath()),
                "avatar-" + variant.getVariantName() + "." + variant.getExtension(),
                variant.getContentType(),
                variant.getSha256() == null ? null : '"' + variant.getSha256() + '"'
        );
    }

    public StoredImageAssetDto storeImageAsset(
            MultipartFile file,
            ImageAssetProcessor.Profile profile,
            StorageCategory category,
            String relatedModule,
            Long relatedEntityId) {
        StorageCategory resolvedCategory = requireCategory(category);
        if (!resolvedCategory.acceptsImage()) {
            throw new BadRequestException("Storage category does not accept images");
        }
        if (relatedEntityId == null || relatedEntityId <= 0) {
            throw new BadRequestException("Related entity is required for identity images");
        }
        String tenantSlug = requireActiveTenantSlug(resolveCurrentTenantSlug());
        StoredImageObjectSet stored = storeImageObjects(
                tenantSlug,
                file,
                profile,
                resolvedCategory,
                String.valueOf(relatedEntityId)
        );
        try {
            StoredFileMetadata metadata = new StoredFileMetadata();
            StoredImageObjectSet.StoredImageObject original = stored.original();
            metadata.setOriginalFilename(stored.originalFilename());
            metadata.setStoredFilename("original." + original.extension());
            metadata.setRelativePath(original.relativePath());
            metadata.setExtension(original.extension());
            metadata.setContentType(original.contentType());
            metadata.setFileSize(original.fileSize());
            metadata.setSha256(original.sha256());
            metadata.setWidth(original.width());
            metadata.setHeight(original.height());
            metadata.setTransformationVersion(stored.transformationVersion());
            metadata.setUploadedByUserId(currentUserIdOrNull());
            metadata.setStorageCategory(resolvedCategory);
            metadata.setRelatedModule(trimToNull(relatedModule));
            metadata.setRelatedEntityId(relatedEntityId);
            metadata = storedFileMetadataRepository.save(metadata);

            Map<String, String> variants = new LinkedHashMap<>();
            for (StoredImageObjectSet.StoredImageObject storedVariant : stored.variants()) {
                StoredFileVariant variant = new StoredFileVariant();
                variant.setSourceFile(metadata);
                variant.setVariantName(storedVariant.name());
                variant.setRelativePath(storedVariant.relativePath());
                variant.setExtension(storedVariant.extension());
                variant.setContentType(storedVariant.contentType());
                variant.setFileSize(storedVariant.fileSize());
                variant.setSha256(storedVariant.sha256());
                variant.setWidth(storedVariant.width());
                variant.setHeight(storedVariant.height());
                metadata.addVariant(variant);
                variants.put(
                        storedVariant.name(),
                        buildVariantApiUrl(tenantSlug, metadata.getId(), storedVariant.name())
                );
            }
            storedFileMetadataRepository.save(metadata);
            return new StoredImageAssetDto(
                    String.valueOf(metadata.getId()),
                    buildFileApiUrl(tenantSlug, metadata.getId(), "preview"),
                    Map.copyOf(variants),
                    metadata.getWidth(),
                    metadata.getHeight(),
                    metadata.getContentType()
            );
        } catch (RuntimeException exception) {
            deleteWrittenPaths(tenantSlug, stored.allPaths());
            throw exception;
        }
    }

    /**
     * The single image upload pipeline used by tenant assets and master-owned branding assets.
     * Callers own their database metadata; this method owns validation, decoding, transforms,
     * provider writes, partial-write cleanup, and transaction rollback cleanup.
     */
    public StoredImageObjectSet storeImageObjects(
            String tenantSlug,
            MultipartFile file,
            ImageAssetProcessor.Profile profile,
            StorageCategory category,
            String... ownerSegments) {
        StorageCategory resolvedCategory = requireCategory(category);
        if (!resolvedCategory.acceptsImage()) {
            throw new BadRequestException("Storage category does not accept images");
        }
        if (profile == null) {
            throw new BadRequestException("Image processing profile is required");
        }

        String normalizedTenantSlug = normalizeTenantSlug(tenantSlug);
        List<String> pathSegments = new ArrayList<>(resolvedCategory.directorySegments());
        pathSegments.addAll(normalizePathSegments(ownerSegments));
        String publicId = UUID.randomUUID().toString();
        pathSegments.add(publicId);
        String basePath = String.join("/", pathSegments);

        ImageAssetProcessor.ProcessedImage processed = imageAssetProcessor.process(file, profile);
        storageProvider.initializeTenant(normalizedTenantSlug, List.of(resolvedCategory.directorySegments()));
        List<String> writtenPaths = new ArrayList<>();
        try {
            String originalPath = basePath + "/original." + processed.extension();
            storageProvider.write(normalizedTenantSlug, originalPath, processed.bytes());
            writtenPaths.add(originalPath);
            StoredImageObjectSet.StoredImageObject original = toStoredImageObject(
                    "original",
                    originalPath,
                    processed.extension(),
                    processed.contentType(),
                    processed.bytes().length,
                    processed.sha256(),
                    processed.width(),
                    processed.height()
            );

            List<StoredImageObjectSet.StoredImageObject> variants = new ArrayList<>();
            for (ImageAssetProcessor.ImageVariant variant : processed.variants()) {
                String variantPath = basePath + "/" + variant.name() + "." + variant.extension();
                storageProvider.write(normalizedTenantSlug, variantPath, variant.bytes());
                writtenPaths.add(variantPath);
                variants.add(toStoredImageObject(
                        variant.name(),
                        variantPath,
                        variant.extension(),
                        variant.contentType(),
                        variant.bytes().length,
                        variant.sha256(),
                        variant.width(),
                        variant.height()
                ));
            }

            StoredImageObjectSet stored = new StoredImageObjectSet(
                    publicId,
                    processed.originalFilename(),
                    original,
                    variants,
                    2
            );
            registerRollbackCleanup(normalizedTenantSlug, stored.allPaths());
            return stored;
        } catch (RuntimeException exception) {
            deleteWrittenPaths(normalizedTenantSlug, writtenPaths);
            throw exception;
        }
    }

    private StoredImageObjectSet.StoredImageObject toStoredImageObject(
            String name,
            String relativePath,
            String extension,
            String contentType,
            int fileSize,
            String sha256,
            int width,
            int height) {
        return new StoredImageObjectSet.StoredImageObject(
                name,
                relativePath,
                extension,
                contentType,
                fileSize,
                sha256,
                width,
                height
        );
    }

    public void supersedeImageAsset(StoredFileMetadata metadata) {
        if (metadata == null || !metadata.isActive()) return;
        metadata.setActive(false);
        metadata.setLifecycleState("SUPERSEDED");
        storedFileMetadataRepository.save(metadata);
    }

    public StoredImageAssetDto toImageAssetDto(StoredFileMetadata metadata, String preferredVariant) {
        if (metadata == null || !metadata.isActive()) return null;
        String tenantSlug = resolveCurrentTenantSlug();
        Map<String, String> variants = new LinkedHashMap<>();
        storedFileVariantRepository.findBySourceFileIdOrderByWidthAsc(metadata.getId())
                .forEach(variant -> variants.put(
                        variant.getVariantName(),
                        buildVariantApiUrl(tenantSlug, metadata.getId(), variant.getVariantName())
                ));
        String url = variants.getOrDefault(
                preferredVariant,
                buildFileApiUrl(tenantSlug, metadata.getId(), "preview")
        );
        return new StoredImageAssetDto(
                String.valueOf(metadata.getId()),
                url,
                Map.copyOf(variants),
                metadata.getWidth(),
                metadata.getHeight(),
                metadata.getContentType()
        );
    }

    public String toImageVariantUrl(StoredFileMetadata metadata, String variant) {
        if (metadata == null || !metadata.isActive()) return null;
        if (metadata.getTransformationVersion() == null || metadata.getTransformationVersion() < 2) {
            return buildFileApiUrl(resolveCurrentTenantSlug(), metadata.getId(), "preview");
        }
        return buildVariantApiUrl(resolveCurrentTenantSlug(), metadata.getId(), variant);
    }

    public StoredFileDto replace(Long id, MultipartFile file) {
        StoredFileMetadata current = getMetadata(id);
        storedFileAccessPolicy.requireWrite(current);
        String tenantSlug = resolveCurrentTenantSlug();
        ValidatedFile replacement = validateFile(file, current.getStorageCategory());
        List<String> relativeSegments = new ArrayList<>(current.getStorageCategory().directorySegments());
        relativeSegments.add(replacement.storedFileName());
        String replacementPath = String.join("/", relativeSegments);
        String previousPath = current.getRelativePath();

        storageProvider.write(tenantSlug, replacementPath, replacement.bytes());
        try {
            current.setOriginalFilename(replacement.originalName());
            current.setStoredFilename(replacement.storedFileName());
            current.setRelativePath(replacementPath);
            current.setExtension(replacement.extension());
            current.setContentType(replacement.contentType());
            current.setFileSize(replacement.size());
            StoredFileMetadata saved = storedFileMetadataRepository.save(current);
            if (!previousPath.equals(replacementPath)) storageProvider.delete(tenantSlug, previousPath);
            return toDto(tenantSlug, saved);
        } catch (RuntimeException exception) {
            storageProvider.delete(tenantSlug, replacementPath);
            throw exception;
        }
    }

    public void delete(Long id) {
        StoredFileMetadata metadata = getMetadata(id);
        storedFileAccessPolicy.requireWrite(metadata);
        if (metadata.getRelatedEntityId() != null) {
            throw new BadRequestException("Delete linked files through the owning module");
        }
        delete(INTERNAL_ID_PREFIX + id);
    }

    public void link(String storedReference, String relatedModule, Long relatedEntityId, StorageCategory category) {
        Long metadataId = parseMetadataId(storedReference);
        if (metadataId == null) return;
        StoredFileMetadata metadata = getMetadata(metadataId);
        link(metadata, relatedModule, relatedEntityId, category);
    }

    public void claimAndLink(String storedReference, String relatedModule, Long relatedEntityId, StorageCategory category) {
        Long metadataId = parseMetadataId(storedReference);
        if (metadataId == null) return;
        StoredFileMetadata metadata = getMetadata(metadataId);
        storedFileAccessPolicy.requireWrite(metadata);
        link(metadata, relatedModule, relatedEntityId, category);
    }

    public List<StoredFileDto> listLinkedFiles(String relatedModule, Long relatedEntityId) {
        String module = trimToNull(relatedModule);
        if (module == null || relatedEntityId == null || relatedEntityId <= 0) return List.of();
        String tenantSlug = resolveCurrentTenantSlug();
        return storedFileMetadataRepository
                .findByRelatedModuleAndRelatedEntityIdAndActiveTrueOrderByUploadedAtAsc(module, relatedEntityId)
                .stream()
                .peek(storedFileAccessPolicy::requireRead)
                .map(metadata -> toDto(tenantSlug, metadata))
                .toList();
    }

    private void link(StoredFileMetadata metadata, String relatedModule, Long relatedEntityId, StorageCategory category) {
        if (category != null) metadata.setStorageCategory(category);
        metadata.setRelatedModule(trimToNull(relatedModule));
        metadata.setRelatedEntityId(relatedEntityId);
        storedFileMetadataRepository.save(metadata);
    }

    private ValidatedFile validateFile(MultipartFile file, StorageCategory category) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        byte[] fileBytes = readFileBytes(file);
        rejectExecutableSignature(fileBytes);

        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String extension = extractExtension(originalName);
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Executable files are not allowed");
        }

        FileKind fileKind = resolveFileKind(extension);
        if (fileKind == FileKind.IMAGE && !category.acceptsImage()) {
            throw new BadRequestException("This storage category only accepts document files");
        }
        if (fileKind == FileKind.DOCUMENT && !category.acceptsDocument()) {
            throw new BadRequestException("This storage category only accepts image files");
        }

        long maxSize = fileKind == FileKind.IMAGE ? storageProperties.maxImageSizeBytes() : storageProperties.maxDocumentSizeBytes();
        if (fileBytes.length > maxSize) {
            String sizeLabel = fileKind == FileKind.IMAGE ? "2MB" : "10MB";
            throw new BadRequestException("File size exceeds " + sizeLabel + " limit");
        }

        String contentType = detectContentType(extension, fileBytes);
        String id = UUID.randomUUID().toString();
        String storedFileName = id + "_" + sanitizeFilename(originalName, extension);
        return new ValidatedFile(id, originalName, extension, storedFileName, contentType, fileBytes, fileBytes.length);
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read file", ex);
        }
    }

    private FileKind resolveFileKind(String extension) {
        if (containsIgnoreCase(storageProperties.getAllowedImageTypes(), extension)) {
            return FileKind.IMAGE;
        }
        if (containsIgnoreCase(storageProperties.getAllowedDocumentTypes(), extension)) {
            return FileKind.DOCUMENT;
        }
        throw new BadRequestException("File type is not allowed");
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        if (values == null || candidate == null) {
            return false;
        }
        String normalizedCandidate = candidate.trim().toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value != null)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalizedCandidate::equals);
    }

    private String detectContentType(String extension, byte[] fileBytes) {
        return switch (extension) {
            case "jpg", "jpeg" -> {
                imageAssetProcessor.validateDecoded(fileBytes, extension);
                yield "image/jpeg";
            }
            case "png" -> {
                imageAssetProcessor.validateDecoded(fileBytes, extension);
                yield "image/png";
            }
            case "webp" -> {
                imageAssetProcessor.validateDecoded(fileBytes, extension);
                yield "image/webp";
            }
            case "pdf" -> {
                validatePdf(fileBytes);
                yield "application/pdf";
            }
            case "docx" -> {
                validateZipContainer(fileBytes, "word/document.xml", "DOCX document");
                yield "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            case "xlsx" -> {
                validateZipContainer(fileBytes, "xl/workbook.xml", "XLSX spreadsheet");
                yield "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
            case "pptx" -> {
                validateZipContainer(fileBytes, "ppt/presentation.xml", "PPTX presentation");
                yield "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            }
            case "zip" -> {
                validateZipContainer(fileBytes, null, "ZIP archive");
                yield "application/zip";
            }
            default -> throw new BadRequestException("File type is not allowed");
        };
    }

    private String detectContentTypeFromName(String fileName) {
        String extension = extractExtension(fileName);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private void validateJpeg(byte[] fileBytes) {
        if (fileBytes.length < 3
                || (fileBytes[0] & 0xFF) != 0xFF
                || (fileBytes[1] & 0xFF) != 0xD8
                || (fileBytes[2] & 0xFF) != 0xFF) {
            throw new BadRequestException("Uploaded file content does not match a JPEG image");
        }
    }

    private void validatePng(byte[] fileBytes) {
        byte[] signature = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (fileBytes.length < signature.length) {
            throw new BadRequestException("Uploaded file content does not match a PNG image");
        }
        for (int i = 0; i < signature.length; i++) {
            if (fileBytes[i] != signature[i]) {
                throw new BadRequestException("Uploaded file content does not match a PNG image");
            }
        }
    }

    private void validateWebp(byte[] fileBytes) {
        if (fileBytes.length < 12
                || fileBytes[0] != 'R'
                || fileBytes[1] != 'I'
                || fileBytes[2] != 'F'
                || fileBytes[3] != 'F'
                || fileBytes[8] != 'W'
                || fileBytes[9] != 'E'
                || fileBytes[10] != 'B'
                || fileBytes[11] != 'P') {
            throw new BadRequestException("Uploaded file content does not match a WEBP image");
        }
    }

    private void validatePdf(byte[] fileBytes) {
        if (fileBytes.length < 5
                || fileBytes[0] != '%'
                || fileBytes[1] != 'P'
                || fileBytes[2] != 'D'
                || fileBytes[3] != 'F'
                || fileBytes[4] != '-') {
            throw new BadRequestException("Uploaded file content does not match a PDF document");
        }
    }

    private void validateZipContainer(byte[] fileBytes, String requiredEntry, String label) {
        if (fileBytes.length < 4 || fileBytes[0] != 'P' || fileBytes[1] != 'K') {
            throw new BadRequestException("Uploaded file content does not match a " + label);
        }

        boolean hasContentTypes = false;
        boolean hasRequiredEntry = requiredEntry == null;
        int entryCount = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                String entryName = entry.getName() == null ? "" : entry.getName().replace("\\", "/");
                validateZipEntryName(entryName);
                if ("[Content_Types].xml".equals(entryName)) {
                    hasContentTypes = true;
                }
                if (entryName.equals(requiredEntry)) {
                    hasRequiredEntry = true;
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Uploaded file content does not match a " + label, ex);
        }

        if (entryCount == 0 || (requiredEntry != null && (!hasContentTypes || !hasRequiredEntry))) {
            throw new BadRequestException("Uploaded file content does not match a " + label);
        }
    }

    private void validateZipEntryName(String entryName) {
        if (entryName.isBlank() || entryName.startsWith("/") || entryName.contains("../") || entryName.contains("..\\")) {
            throw new BadRequestException("Archive contains an unsafe file path");
        }
        String extension = extractOptionalExtension(entryName);
        if (extension != null && DANGEROUS_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Archive contains an executable file");
        }
    }

    private void rejectExecutableSignature(byte[] fileBytes) {
        if (fileBytes.length >= 2 && fileBytes[0] == 'M' && fileBytes[1] == 'Z') {
            throw new BadRequestException("Executable files are not allowed");
        }
        if (fileBytes.length >= 4
                && (fileBytes[0] & 0xFF) == 0x7F
                && fileBytes[1] == 'E'
                && fileBytes[2] == 'L'
                && fileBytes[3] == 'F') {
            throw new BadRequestException("Executable files are not allowed");
        }
        if (fileBytes.length >= 4
                && (((fileBytes[0] & 0xFF) == 0xFE && (fileBytes[1] & 0xFF) == 0xED && (fileBytes[2] & 0xFF) == 0xFA && (fileBytes[3] & 0xFF) == 0xCE)
                || ((fileBytes[0] & 0xFF) == 0xCE && (fileBytes[1] & 0xFF) == 0xFA && (fileBytes[2] & 0xFF) == 0xED && (fileBytes[3] & 0xFF) == 0xFE))) {
            throw new BadRequestException("Executable files are not allowed");
        }
    }

    private String resolveCurrentTenantSlug() {
        String tenantSlug = trimToNull(TenantContextHolder.getTenantSlug());
        if (tenantSlug != null) {
            return normalizeTenantSlug(tenantSlug);
        }

        String tenantKey = trimToNull(TenantContextHolder.getTenantKey());
        if (tenantKey == null) {
            tenantKey = securityUtils.getCurrentTenantKeyOrThrow();
        }

        String normalizedTenantKey = normalizeTenantSlug(tenantKey);
        return masterTenantLookupService.findByTenantKey(normalizedTenantKey)
                .map(PlatformTenant::getSlug)
                .map(this::normalizeTenantSlug)
                .orElse(normalizedTenantKey);
    }

    private String requireActiveTenantSlug(String tenantSlug) {
        String normalizedTenantSlug = normalizeTenantSlug(tenantSlug);
        findActiveTenantBySlug(normalizedTenantSlug);
        return normalizedTenantSlug;
    }

    private PlatformTenant findActiveTenantBySlug(String tenantSlug) {
        PlatformTenant tenant = masterTenantLookupService.findBySlug(tenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantSlug));
        if (tenant.getStatus() != TenantStatus.ACTIVE || Boolean.FALSE.equals(tenant.getActive())) {
            throw new ForbiddenOperationException("Tenant is not active for file access");
        }
        return tenant;
    }

    private StorageCategory requireCategory(StorageCategory category) {
        if (category == null) {
            throw new BadRequestException("Storage category is required");
        }
        return category;
    }

    private StoredFileMetadata getMetadata(Long id) {
        if (id == null || id <= 0) throw new BadRequestException("File id must be positive");
        return storedFileMetadataRepository.findById(id)
                .filter(StoredFileMetadata::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    private StoredFileDto toDto(String tenantSlug, StoredFileMetadata metadata) {
        Instant uploadedAt = metadata.getUploadedAt() == null
                ? Instant.now()
                : metadata.getUploadedAt().atZone(java.time.ZoneId.systemDefault()).toInstant();
        return new StoredFileDto(
                String.valueOf(metadata.getId()),
                metadata.getOriginalFilename(),
                metadata.getRelativePath(),
                buildFileApiUrl(tenantSlug, metadata.getId(), "preview"),
                metadata.getFileSize(),
                metadata.getContentType(),
                uploadedAt);
    }

    private StoredFileResource resource(String tenantSlug, StoredFileMetadata metadata) {
        Resource resource = storageProvider.read(tenantSlug, metadata.getRelativePath());
        return new StoredFileResource(
                resource,
                metadata.getOriginalFilename(),
                metadata.getContentType(),
                metadata.getSha256() == null ? null : '"' + metadata.getSha256() + '"'
        );
    }

    private void requireIntegrity(String tenantSlug, String relativePath, String sha256) {
        if (sha256 == null) return;
        if (!storageProvider.hashMatches(tenantSlug, relativePath, sha256)) {
            observability.recordFallback("stored_object_integrity_failure");
            throw new ResourceNotFoundException("File not found");
        }
    }

    private Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof PlatformUserPrincipal principal
                ? principal.getId()
                : null;
    }

    private String buildFileApiUrl(String tenantSlug, Long id, String operation) {
        return "/api/" + encodePathSegment(normalizeTenantSlug(tenantSlug)) + "/files/" + id + "/" + operation;
    }

    private String buildVariantApiUrl(String tenantSlug, Long id, String variant) {
        return "/api/" + encodePathSegment(normalizeTenantSlug(tenantSlug)) + "/files/" + id
                + "/variants/" + encodePathSegment(variant) + "/preview";
    }

    private void registerRollbackCleanup(String tenantSlug, List<String> paths) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) return;
        List<String> immutablePaths = List.copyOf(paths);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) deleteWrittenPaths(tenantSlug, immutablePaths);
                    }
                }
        );
    }

    private void deleteWrittenPaths(String tenantSlug, List<String> paths) {
        for (String path : paths) {
            try {
                storageProvider.delete(tenantSlug, path);
            } catch (RuntimeException ignored) {
                // Reconciliation will retry cleanup if provider deletion fails.
            }
        }
    }

    private Long parseMetadataId(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        String idValue = null;
        if (normalized.startsWith(INTERNAL_ID_PREFIX)) {
            idValue = normalized.substring(INTERNAL_ID_PREFIX.length());
        } else {
            String path = extractUriPath(normalized);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^/api/[a-z0-9][a-z0-9-]{0,79}/files/(\\d+)(?:/(?:preview|download))?$")
                    .matcher(path);
            if (matcher.matches()) idValue = matcher.group(1);
        }
        if (idValue == null) return null;
        try {
            long id = Long.parseLong(idValue);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Invalid file reference");
        }
    }

    private String normalizeStoredName(String storedName) {
        String normalized = trimToNull(storedName);
        if (normalized == null) {
            throw new BadRequestException("storedName is required");
        }
        if (isLocalReference(normalized)) {
            return normalizeRelativePath(normalized.substring(INTERNAL_PREFIX.length()));
        }
        String parsedPublicReference = parsePublicFileUrl(normalized);
        if (parsedPublicReference != null) {
            return parsedPublicReference;
        }
        return normalizeRelativePath(normalized);
    }

    private String parsePublicFileUrl(String value) {
        String path = extractUriPath(value);
        if (path.startsWith("/files/")) {
            String remainder = path.substring("/files/".length());
            int slashIndex = remainder.indexOf('/');
            if (slashIndex <= 0 || slashIndex == remainder.length() - 1) {
                throw new BadRequestException("Invalid file URL");
            }
            normalizeTenantSlug(remainder.substring(0, slashIndex));
            return normalizeRelativePath(remainder.substring(slashIndex + 1));
        }
        if (path.startsWith("/api/public/")) {
            String remainder = path.substring("/api/public/".length());
            int filesIndex = remainder.indexOf("/files/");
            if (filesIndex <= 0 || filesIndex + "/files/".length() >= remainder.length()) {
                return null;
            }
            normalizeTenantSlug(remainder.substring(0, filesIndex));
            return normalizeRelativePath(remainder.substring(filesIndex + "/files/".length()));
        }
        return null;
    }

    private String extractUriPath(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getPath() != null) {
                return uri.getPath();
            }
        } catch (IllegalArgumentException ignored) {
            // Treat as a local path below.
        }
        return value;
    }

    private String normalizeRelativePath(String relativePath) {
        String normalized = trimToNull(relativePath);
        if (normalized == null) {
            throw new BadRequestException("File path is required");
        }
        normalized = normalized.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        String[] rawSegments = normalized.split("/");
        List<String> segments = new ArrayList<>();
        for (String rawSegment : rawSegments) {
            String segment = trimToNull(rawSegment);
            if (segment == null || ".".equals(segment) || "..".equals(segment) || segment.contains("..")) {
                throw new BadRequestException("Invalid file path");
            }
            segments.add(segment);
        }
        if (segments.size() < 2 || !ALLOWED_ROOT_DIRECTORIES.contains(segments.get(0))) {
            throw new BadRequestException("Invalid storage folder");
        }
        return String.join("/", segments);
    }

    private List<String> normalizePathSegments(String... pathSegments) {
        List<String> normalizedSegments = new ArrayList<>();
        if (pathSegments == null) {
            return normalizedSegments;
        }
        for (String pathSegment : pathSegments) {
            String normalized = trimToNull(pathSegment);
            if (normalized == null) {
                continue;
            }
            normalizedSegments.add(normalizePathSegment(normalized));
        }
        return normalizedSegments;
    }

    private String normalizePathSegment(String pathSegment) {
        String normalized = trimToNull(pathSegment);
        if (normalized == null || normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new BadRequestException("Invalid file path segment");
        }
        String sanitized = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            throw new BadRequestException("Invalid file path segment");
        }
        return sanitized;
    }

    private String normalizeOriginalName(String originalFilename) {
        String cleaned = StringUtils.cleanPath(originalFilename == null || originalFilename.isBlank() ? "file" : originalFilename);
        String stripped = stripPath(cleaned);
        return stripped.isBlank() ? "file" : stripped;
    }

    private String sanitizeFilename(String filename, String extension) {
        String stripped = stripPath(filename);
        String sanitized = stripped.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "file." + extension;
        }
        if (!sanitized.toLowerCase(Locale.ROOT).endsWith("." + extension)) {
            sanitized = sanitized + "." + extension;
        }
        return sanitized;
    }

    private String stripPath(String filename) {
        String normalized = filename.replace("\\", "/");
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private String extractExtension(String filename) {
        String extension = extractOptionalExtension(filename);
        if (extension == null) {
            throw new BadRequestException("File extension is required");
        }
        return extension;
    }

    private String extractOptionalExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return null;
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String fileNameFromStoredName(String storedName) {
        String relativePath = normalizeRelativePath(storedName);
        int slashIndex = relativePath.lastIndexOf('/');
        return slashIndex >= 0 ? relativePath.substring(slashIndex + 1) : relativePath;
    }

    private String normalizeTenantSlug(String tenantSlug) {
        String normalized = trimToNull(tenantSlug);
        if (normalized == null) {
            throw new BadRequestException("Tenant slug is required");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9-]{0,79}")) {
            throw new BadRequestException("Invalid tenant slug");
        }
        return normalized;
    }

    private String normalizeOptionalIdentifier(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String encodeRelativePath(String relativePath) {
        String normalized = normalizeRelativePath(relativePath);
        String[] segments = normalized.split("/");
        List<String> encodedSegments = new ArrayList<>();
        for (String segment : segments) {
            encodedSegments.add(encodePathSegment(segment));
        }
        return String.join("/", encodedSegments);
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
