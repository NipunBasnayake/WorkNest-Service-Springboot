package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);

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
            verifyWritable(root);
            log.info("Local file storage initialized at {}", root);
        } catch (IOException exception) {
            log.error("Unable to initialize local file storage at {}", root, exception);
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
            log.debug("Tenant storage directories initialized tenant={} root={}", tenantSlug, tenantRoot);
        } catch (IOException exception) {
            log.error("Unable to initialize tenant storage directories tenant={} root={}",
                    tenantSlug, tenantRoot, exception);
            throw new IllegalStateException("Unable to initialize tenant storage directories", exception);
        }
    }

    @Override
    public void write(String tenantSlug, String relativePath, byte[] content) {
        Path target = resolve(tenantSlug, relativePath);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "." + target.getFileName() + ".", ".tmp");
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            if (!Files.isRegularFile(target) || Files.size(target) != content.length) {
                Files.deleteIfExists(target);
                throw new IOException("Stored file verification failed");
            }
            log.info("Stored file tenant={} path={} bytes={}", tenantSlug, relativePath, content.length);
        } catch (IOException exception) {
            log.error("Failed to store file tenant={} path={} bytes={}",
                    tenantSlug, relativePath, content == null ? 0 : content.length, exception);
            throw new BadRequestException("Failed to store file", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupException) {
                    log.warn("Failed to remove temporary storage file {}", temporary, cleanupException);
                }
            }
        }
    }

    @Override
    public Resource read(String tenantSlug, String relativePath) {
        Path target = resolve(tenantSlug, relativePath);
        if (!Files.isRegularFile(target)) {
            log.warn("Stored file is missing tenant={} path={}", tenantSlug, relativePath);
            throw new ResourceNotFoundException("File not found");
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.isReadable()) {
                log.warn("Stored file is not readable tenant={} path={}", tenantSlug, relativePath);
                throw new ResourceNotFoundException("File not found");
            }
            log.debug("Reading stored file tenant={} path={}", tenantSlug, relativePath);
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
            boolean deleted = Files.deleteIfExists(resolve(tenantSlug, relativePath));
            log.info("Deleted stored file tenant={} path={} existed={}", tenantSlug, relativePath, deleted);
        } catch (IOException exception) {
            log.error("Failed to delete stored file tenant={} path={}", tenantSlug, relativePath, exception);
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

    private void verifyWritable(Path directory) throws IOException {
        Path probe = Files.createTempFile(directory, ".worknest-storage-probe-", ".tmp");
        try {
            Files.write(probe, new byte[] {0x57, 0x4E}, StandardOpenOption.TRUNCATE_EXISTING);
        } finally {
            Files.deleteIfExists(probe);
        }
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
