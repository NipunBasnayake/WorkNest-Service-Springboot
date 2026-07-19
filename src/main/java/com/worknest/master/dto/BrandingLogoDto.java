package com.worknest.master.dto;

import java.util.Map;

public record BrandingLogoDto(
        String assetId,
        String url,
        Map<String, String> variants,
        String altText,
        Integer width,
        Integer height) {
}
