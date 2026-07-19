package com.worknest.common.storage;

import java.time.Instant;

/** Provider-neutral object metadata used by reconciliation without exposing filesystem paths. */
public record StoredObjectDescriptor(
        String relativePath,
        long size,
        Instant lastModified) {
}
