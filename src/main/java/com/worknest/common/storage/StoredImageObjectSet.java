package com.worknest.common.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider-neutral description of one processed source image and its generated variants.
 * The bytes have already been written by {@link FileStorageService} when this value is returned.
 */
public record StoredImageObjectSet(
        String publicId,
        String originalFilename,
        StoredImageObject original,
        List<StoredImageObject> variants,
        int transformationVersion) {

    public StoredImageObjectSet {
        variants = List.copyOf(variants);
    }

    public List<String> allPaths() {
        List<String> paths = new ArrayList<>(variants.size() + 1);
        paths.add(original.relativePath());
        variants.stream().map(StoredImageObject::relativePath).forEach(paths::add);
        return List.copyOf(paths);
    }

    public record StoredImageObject(
            String name,
            String relativePath,
            String extension,
            String contentType,
            long fileSize,
            String sha256,
            int width,
            int height) {
    }
}
