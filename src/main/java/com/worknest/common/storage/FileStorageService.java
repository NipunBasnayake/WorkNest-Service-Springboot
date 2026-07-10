package com.worknest.common.storage;

import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.multitenancy.context.TenantContextHolder;
import com.worknest.security.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final long SIGNED_URL_TTL_SECONDS = 30L * 60L;
    private static final String INTERNAL_PREFIX = "wnfile://";
    private static final String STORAGE_BUCKET = "worknest-local";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf", "docx");

    private final Path storageRootPath;
    private final String signedUrlSecret;
    private final SecurityUtils securityUtils;
    private final MasterTenantLookupService masterTenantLookupService;

    public record StoredFileResult(
            String url,
            String path,
            String name,
            String mimeType,
            long size,
            String bucket,
            String uploadedAt) {
    }

    public record StoredFileResource(Resource resource, String fileName, String mimeType) {
    }

    private record LocalFileRef(String folderName, String fileName) {
        private String path() {
            return folderName + "/" + fileName;
        }
    }

    public FileStorageService(
            @Value("${app.storage.local-uploads-dir:./worknest-uploads}") String localUploadsDir,
            @Value("${app.storage.signed-url-secret:${app.jwt.secret:worknest-dev-file-secret}}") String signedUrlSecret,
            SecurityUtils securityUtils,
            MasterTenantLookupService masterTenantLookupService) {
        this.storageRootPath = Paths.get(localUploadsDir).toAbsolutePath().normalize();
        this.signedUrlSecret = signedUrlSecret == null || signedUrlSecret.isBlank()
                ? "worknest-dev-file-secret"
                : signedUrlSecret;
        this.securityUtils = securityUtils;
        this.masterTenantLookupService = masterTenantLookupService;
        initializeRootDirectory();
    }

    public StoredFileResult store(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        byte[] fileBytes = readFileBytes(file);
        if (fileBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds 10MB limit");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String extension = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Only jpg, jpeg, png, pdf, and docx files are allowed");
        }

        String normalizedType = normalizeType(type);
        if ("image".equals(normalizedType) && !Set.of("jpg", "jpeg", "png").contains(extension)) {
            throw new BadRequestException("Only jpg, jpeg, and png files are allowed for image type");
        }

        String mimeType = detectMimeType(extension, fileBytes);
        String folderName = "image".equals(normalizedType) ? "images" : "docs";
        String currentTenantKey = resolveCurrentTenantKey();
        Path targetDirectory = tenantFolder(currentTenantKey, folderName);

        String filename = UUID.randomUUID() + "_" + sanitizeFilename(stripPath(originalName), extension);
        Path targetPath = targetDirectory.resolve(filename).normalize();
        if (!targetPath.startsWith(targetDirectory)) {
            throw new BadRequestException("Invalid file path");
        }

        try {
            Files.createDirectories(targetDirectory);
            Files.write(targetPath, fileBytes);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to store file");
        }

        String storedReference = INTERNAL_PREFIX + folderName + "/" + filename;
        return new StoredFileResult(
                toPublicUrl(storedReference),
                storedReference,
                filename,
                mimeType,
                fileBytes.length,
                STORAGE_BUCKET,
                Instant.now().toString()
        );
    }

    public String normalizeStoredReference(String fileUrl) {
        String normalized = trimToNull(fileUrl);
        if (normalized == null) {
            throw new BadRequestException("fileUrl is required");
        }
        if (isLocalReference(normalized)) {
            parseLocalReference(normalized);
            return normalized;
        }
        String parsedPublicReference = parsePublicFileUrl(normalized);
        if (parsedPublicReference != null) {
            return parsedPublicReference;
        }
        if (normalized.startsWith("/uploads/")) {
            return INTERNAL_PREFIX + normalizeLegacyUploadPath(normalized.substring("/uploads/".length()));
        }
        return normalized;
    }

    public String toPublicUrl(String storedReference) {
        String normalized = trimToNull(storedReference);
        if (normalized == null || !isLocalReference(normalized)) {
            return normalized;
        }

        LocalFileRef ref = parseLocalReference(normalized);
        String tenantSlug = trimToNull(TenantContextHolder.getTenantSlug());
        if (tenantSlug == null) {
            tenantSlug = securityUtils.getCurrentTenantKeyOrThrow();
        }

        long expiresAt = Instant.now().plusSeconds(SIGNED_URL_TTL_SECONDS).getEpochSecond();
        String signature = sign(tenantSlug, ref.path(), expiresAt);
        return "/api/public/" + UriUtils.encodePathSegment(tenantSlug, StandardCharsets.UTF_8)
                + "/files/" + ref.folderName()
                + "/" + UriUtils.encodePathSegment(ref.fileName(), StandardCharsets.UTF_8)
                + "?expires=" + expiresAt
                + "&signature=" + signature;
    }

    public boolean isLocalReference(String value) {
        return value != null && value.startsWith(INTERNAL_PREFIX);
    }

    public StoredFileResource loadAsResource(String storedReference) {
        LocalFileRef ref = parseLocalReference(storedReference);
        String tenantKey = resolveCurrentTenantKey();
        return loadFromTenant(tenantKey, ref);
    }

    public StoredFileResource loadPublicResource(
            String tenantSlug,
            String folderName,
            String fileName,
            long expires,
            String signature) {
        String normalizedTenantSlug = normalizeIdentifier(tenantSlug);
        LocalFileRef ref = new LocalFileRef(normalizeFolderName(folderName), normalizeFileName(fileName));
        validateSignature(normalizedTenantSlug, ref.path(), expires, signature);

        PlatformTenant tenant = masterTenantLookupService.findBySlug(normalizedTenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + normalizedTenantSlug));
        if (tenant.getStatus() != TenantStatus.ACTIVE || Boolean.FALSE.equals(tenant.getActive())) {
            throw new ForbiddenOperationException("Tenant is not active for file access");
        }

        return loadFromTenant(tenant.getTenantKey(), ref);
    }

    private StoredFileResource loadFromTenant(String tenantKey, LocalFileRef ref) {
        Path targetDirectory = tenantFolder(tenantKey, ref.folderName());
        Path filePath = targetDirectory.resolve(ref.fileName()).normalize();
        if (!filePath.startsWith(targetDirectory)) {
            throw new BadRequestException("Invalid file path");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ResourceNotFoundException("File not found");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("File not found");
            }
            return new StoredFileResource(resource, ref.fileName(), detectMimeTypeFromName(ref.fileName()));
        } catch (MalformedURLException ex) {
            throw new BadRequestException("Invalid file path");
        }
    }

    private void initializeRootDirectory() {
        try {
            Files.createDirectories(storageRootPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize upload directory", ex);
        }
    }

    private Path tenantFolder(String tenantKey, String folderName) {
        return storageRootPath
                .resolve(normalizeIdentifier(tenantKey))
                .resolve(normalizeFolderName(folderName))
                .normalize();
    }

    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Failed to read file");
        }
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (!"image".equals(normalized) && !"doc".equals(normalized)) {
            throw new BadRequestException("Type must be 'image' or 'doc'");
        }
        return normalized;
    }

    private String extractExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw new BadRequestException("File extension is required");
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String stripPath(String filename) {
        String normalized = filename.replace("\\", "/");
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private String sanitizeFilename(String filename, String extension) {
        String stripped = stripPath(filename);
        String sanitized = stripped.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "file." + extension;
        }
        return sanitized;
    }

    private String detectMimeType(String extension, byte[] fileBytes) {
        return switch (extension) {
            case "jpg", "jpeg" -> {
                if (fileBytes.length < 3
                        || (fileBytes[0] & 0xFF) != 0xFF
                        || (fileBytes[1] & 0xFF) != 0xD8
                        || (fileBytes[2] & 0xFF) != 0xFF) {
                    throw new BadRequestException("Uploaded file content does not match a JPEG image");
                }
                yield "image/jpeg";
            }
            case "png" -> {
                byte[] signature = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
                if (fileBytes.length < signature.length) {
                    throw new BadRequestException("Uploaded file content does not match a PNG image");
                }
                for (int i = 0; i < signature.length; i++) {
                    if (fileBytes[i] != signature[i]) {
                        throw new BadRequestException("Uploaded file content does not match a PNG image");
                    }
                }
                yield "image/png";
            }
            case "pdf" -> {
                if (fileBytes.length < 5
                        || fileBytes[0] != '%'
                        || fileBytes[1] != 'P'
                        || fileBytes[2] != 'D'
                        || fileBytes[3] != 'F'
                        || fileBytes[4] != '-') {
                    throw new BadRequestException("Uploaded file content does not match a PDF document");
                }
                yield "application/pdf";
            }
            case "docx" -> {
                validateDocx(fileBytes);
                yield "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            default -> throw new BadRequestException("Only jpg, jpeg, png, pdf, and docx files are allowed");
        };
    }

    private String detectMimeTypeFromName(String fileName) {
        String extension = extractExtension(fileName);
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private void validateDocx(byte[] fileBytes) {
        if (fileBytes.length < 4
                || fileBytes[0] != 'P'
                || fileBytes[1] != 'K') {
            throw new BadRequestException("Uploaded file content does not match a DOCX document");
        }

        boolean hasContentTypes = false;
        boolean hasDocument = false;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if ("[Content_Types].xml".equals(entryName)) {
                    hasContentTypes = true;
                }
                if ("word/document.xml".equals(entryName)) {
                    hasDocument = true;
                }
                if (hasContentTypes && hasDocument) {
                    return;
                }
            }
        } catch (IOException ex) {
            throw new BadRequestException("Uploaded file content does not match a DOCX document");
        }

        throw new BadRequestException("Uploaded file content does not match a DOCX document");
    }

    private String resolveCurrentTenantKey() {
        String tenantKey = trimToNull(TenantContextHolder.getTenantKey());
        if (tenantKey != null) {
            return tenantKey;
        }
        return securityUtils.getCurrentTenantKeyOrThrow();
    }

    private LocalFileRef parseLocalReference(String storedReference) {
        String value = trimToNull(storedReference);
        if (value == null || !value.startsWith(INTERNAL_PREFIX)) {
            throw new BadRequestException("Invalid local file reference");
        }

        String path = value.substring(INTERNAL_PREFIX.length()).replace("\\", "/");
        int slashIndex = path.indexOf('/');
        if (slashIndex <= 0 || slashIndex == path.length() - 1 || path.contains("..")) {
            throw new BadRequestException("Invalid local file reference");
        }

        String folderName = normalizeFolderName(path.substring(0, slashIndex));
        String fileName = normalizeFileName(path.substring(slashIndex + 1));
        return new LocalFileRef(folderName, fileName);
    }

    private String parsePublicFileUrl(String value) {
        String marker = "/files/";
        int publicIndex = value.indexOf("/api/public/");
        int markerIndex = value.indexOf(marker, Math.max(publicIndex, 0));
        if (publicIndex < 0 || markerIndex < 0) {
            return null;
        }

        String remainder = value.substring(markerIndex + marker.length());
        int queryIndex = remainder.indexOf('?');
        String path = queryIndex >= 0 ? remainder.substring(0, queryIndex) : remainder;
        return INTERNAL_PREFIX + normalizeLegacyUploadPath(path);
    }

    private String normalizeLegacyUploadPath(String path) {
        String normalized = path == null ? "" : path.trim().replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new BadRequestException("Invalid file path");
        }
        int slashIndex = normalized.indexOf('/');
        if (slashIndex <= 0 || slashIndex == normalized.length() - 1) {
            throw new BadRequestException("Invalid file path");
        }
        return normalizeFolderName(normalized.substring(0, slashIndex))
                + "/" + normalizeFileName(normalized.substring(slashIndex + 1));
    }

    private String normalizeFolderName(String folderName) {
        String normalized = normalizeIdentifier(folderName);
        if (!"images".equals(normalized) && !"docs".equals(normalized)) {
            throw new BadRequestException("Invalid file folder");
        }
        return normalized;
    }

    private String normalizeFileName(String fileName) {
        String normalized = trimToNull(fileName);
        if (normalized == null || normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new BadRequestException("Invalid file name");
        }
        return normalized;
    }

    private String normalizeIdentifier(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException("Tenant or folder identifier is required");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void validateSignature(String tenantSlug, String path, long expires, String signature) {
        if (Instant.now().getEpochSecond() > expires) {
            throw new ForbiddenOperationException("File link has expired");
        }
        String expected = sign(tenantSlug, path, expires);
        if (signature == null || !constantTimeEquals(expected, signature)) {
            throw new ForbiddenOperationException("File link is invalid");
        }
    }

    private String sign(String tenantSlug, String path, long expires) {
        String payload = tenantSlug + "\n" + path + "\n" + expires;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signedUrlSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign file URL", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigestHolder.isEqual(expectedBytes, actualBytes);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class MessageDigestHolder {
        private MessageDigestHolder() {
        }

        private static boolean isEqual(byte[] left, byte[] right) {
            return java.security.MessageDigest.isEqual(left, right);
        }
    }
}
