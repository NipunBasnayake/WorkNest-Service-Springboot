package com.worknest.common.storage;

import java.util.Map;

public record StoredImageAssetDto(
        String assetId,
        String url,
        Map<String, String> variants,
        Integer width,
        Integer height,
        String contentType) {
}
