package com.worknest.common.util;

public final class SlugUtils {

    private SlugUtils() {
        // Utility class
    }

    public static String slugify(String input) {
        if (input == null) {
            return null;
        }

        // 1. Lowercase
        String normalized = input.toLowerCase();

        // 2. Trim spaces
        normalized = normalized.trim();

        // 3. Replace spaces, underscores, and consecutive hyphens with a single hyphen
        normalized = normalized.replaceAll("[\\s_]+", "-");

        // 4. Remove invalid chars (anything that is not a-z, 0-9, or -)
        normalized = normalized.replaceAll("[^a-z0-9\\-]+", "");

        // 5. Clean up multiple hyphens and leading/trailing hyphens
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("^-|-$", "");

        return normalized.isBlank() ? null : normalized;
    }
}
