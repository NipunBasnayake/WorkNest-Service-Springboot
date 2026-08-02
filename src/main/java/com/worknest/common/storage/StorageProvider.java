package com.worknest.common.storage;

import org.springframework.core.io.Resource;

import java.util.Collection;
import java.util.List;

public interface StorageProvider {
    void initialize(Collection<List<String>> categoryDirectories);
    void initializeTenant(String tenantSlug, Collection<List<String>> categoryDirectories);
    String bucketForPath(String relativePath);
    String objectKey(String tenantSlug, String relativePath);
    void write(String tenantSlug, String relativePath, byte[] content);
    Resource read(String tenantSlug, String relativePath);
    boolean exists(String tenantSlug, String relativePath);
    boolean hashMatches(String tenantSlug, String relativePath, String expectedSha256);
    void delete(String tenantSlug, String relativePath);
    String getPublicUrl(String tenantSlug, String relativePath);
    String getSignedUrl(String tenantSlug, String relativePath, java.time.Duration expiresIn);
    void move(String tenantSlug, String sourceRelativePath, String destinationRelativePath);
    void copy(String tenantSlug, String sourceRelativePath, String destinationRelativePath);
    List<StoredObjectDescriptor> listObjects(String tenantSlug, String relativePrefix);
}
