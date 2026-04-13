package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");

    private final Path frontendPublicUploadsPath;

    public record StoredFileResult(String url, String path, String name, String mimeType, long size) {
    }

    public FileStorageService(
            @Value("${app.storage.frontend-public-uploads-dir:../WorkNest-Client-ReactJs/public/uploads}")
            String frontendPublicUploadsDir) {
        // Temporary dev-only storage: backend writes directly into frontend public/uploads.
        this.frontendPublicUploadsPath = Paths.get(frontendPublicUploadsDir).toAbsolutePath().normalize();
        initializeDirectories();
    }

    public StoredFileResult store(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds 5MB limit");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String extension = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Only jpg, jpeg, png, and pdf files are allowed");
        }

        String normalizedType = normalizeType(type);
        if ("image".equals(normalizedType) && "pdf".equals(extension)) {
            throw new BadRequestException("PDF files are not allowed for image type");
        }

        String folderName = "image".equals(normalizedType) ? "images" : "docs";
        Path targetDirectory = frontendPublicUploadsPath.resolve(folderName);

        String filename = UUID.randomUUID() + "_" + stripPath(originalName);
        Path targetPath = targetDirectory.resolve(filename).normalize();
        if (!targetPath.startsWith(targetDirectory)) {
            throw new BadRequestException("Invalid file path");
        }

        try {
            Files.createDirectories(targetDirectory);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to store file");
        }

        String relativePath = folderName + "/" + filename;
        String url = "/uploads/" + relativePath;

        return new StoredFileResult(
                url,
                relativePath,
                filename,
                file.getContentType(),
                file.getSize()
        );
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(frontendPublicUploadsPath.resolve("images"));
            Files.createDirectories(frontendPublicUploadsPath.resolve("docs"));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize upload directories", ex);
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
}
