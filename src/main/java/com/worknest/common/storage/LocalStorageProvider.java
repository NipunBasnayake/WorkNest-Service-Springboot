package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Component
public class LocalStorageProvider implements StorageProvider {
    private final Path root;
    private final Path tenantsRoot;

    public LocalStorageProvider(StorageProperties properties) {
        this.root = properties.rootPath();
        this.tenantsRoot = root.resolve("tenants").normalize();
    }

    @Override
    public void initialize(Collection<List<String>> categoryDirectories) {
        try {
            Files.createDirectories(root);
            Files.createDirectories(tenantsRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize storage directory", exception);
        }
    }

    @Override
    public void initializeTenant(String tenantSlug, Collection<List<String>> categoryDirectories) {
        Path tenantRoot = tenantRoot(tenantSlug);
        try {
            Files.createDirectories(tenantRoot);
            for (List<String> directory : categoryDirectories) {
                Path path = tenantRoot.resolve(String.join("/", directory)).normalize();
                requireInside(path, tenantRoot);
                Files.createDirectories(path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize tenant storage directories", exception);
        }
    }

    @Override
    public void write(String tenantSlug, String relativePath, byte[] content) {
        Path target = resolve(tenantSlug, relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new BadRequestException("Failed to store file", exception);
        }
    }

    @Override
    public Resource read(String tenantSlug, String relativePath) {
        Path target = resolve(tenantSlug, relativePath);
        if (!Files.isRegularFile(target)) throw new ResourceNotFoundException("File not found");
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.isReadable()) throw new ResourceNotFoundException("File not found");
            return resource;
        } catch (MalformedURLException exception) {
            throw new BadRequestException("Invalid file path", exception);
        }
    }

    @Override
    public boolean exists(String tenantSlug, String relativePath) {
        return Files.isRegularFile(resolve(tenantSlug, relativePath));
    }

    @Override
    public boolean hashMatches(String tenantSlug, String relativePath, String expectedSha256) {
        if (expectedSha256 == null || !expectedSha256.matches("(?i)^[0-9a-f]{64}$")) return false;
        Path target = resolve(tenantSlug, relativePath);
        if (!Files.isRegularFile(target)) return false;
        try (var input = Files.newInputStream(target)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            byte[] expected = HexFormat.of().parseHex(expectedSha256);
            return MessageDigest.isEqual(expected, digest.digest());
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public void delete(String tenantSlug, String relativePath) {
        try {
            Files.deleteIfExists(resolve(tenantSlug, relativePath));
        } catch (IOException exception) {
            throw new BadRequestException("Failed to delete file", exception);
        }
    }

    @Override
    public List<StoredObjectDescriptor> listObjects(String tenantSlug, String relativePrefix) {
        Path tenantRoot = tenantRoot(tenantSlug);
        Path prefix = resolve(tenantSlug, relativePrefix);
        if (!Files.exists(prefix)) return List.of();
        List<StoredObjectDescriptor> objects = new ArrayList<>();
        try (var paths = Files.walk(prefix)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String relativePath = tenantRoot.relativize(path).toString().replace('\\', '/');
                    Instant lastModified = Files.getLastModifiedTime(path).toInstant();
                    objects.add(new StoredObjectDescriptor(relativePath, Files.size(path), lastModified));
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to inspect stored object", exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to list stored objects", exception);
        }
        objects.sort(Comparator.comparing(StoredObjectDescriptor::relativePath));
        return List.copyOf(objects);
    }

    @Override
    public Path localTenantRoot() {
        return tenantsRoot;
    }

    private Path resolve(String tenantSlug, String relativePath) {
        Path tenantRoot = tenantRoot(tenantSlug);
        Path target = tenantRoot.resolve(relativePath.replace('\\', '/')).normalize();
        requireInside(target, tenantRoot);
        return target;
    }

    private Path tenantRoot(String tenantSlug) {
        String normalized = tenantSlug == null ? "" : tenantSlug.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9][a-z0-9-]{0,62}")) throw new BadRequestException("Invalid tenant storage key");
        Path path = tenantsRoot.resolve(normalized).normalize();
        requireInside(path, tenantsRoot);
        return path;
    }

    private void requireInside(Path candidate, Path parent) {
        if (!candidate.startsWith(parent)) throw new BadRequestException("Invalid storage path");
    }
}
