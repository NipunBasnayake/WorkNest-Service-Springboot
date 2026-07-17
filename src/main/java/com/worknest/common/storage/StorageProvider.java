package com.worknest.common.storage;

import org.springframework.core.io.Resource;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public interface StorageProvider {
    void initialize(Collection<List<String>> categoryDirectories);
    void initializeTenant(String tenantSlug, Collection<List<String>> categoryDirectories);
    void write(String tenantSlug, String relativePath, byte[] content);
    Resource read(String tenantSlug, String relativePath);
    boolean exists(String tenantSlug, String relativePath);
    void delete(String tenantSlug, String relativePath);
    Path localTenantRoot();
}
