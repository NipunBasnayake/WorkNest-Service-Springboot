package com.worknest.common.storage;

import java.time.Instant;

public record StoredFileDto(
        String id,
        String originalName,
        String storedName,
        String url,
        long size,
        String contentType,
        Instant uploadedAt) {
}