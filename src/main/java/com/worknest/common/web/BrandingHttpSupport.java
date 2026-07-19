package com.worknest.common.web;

import com.worknest.common.exception.BadRequestException;

public final class BrandingHttpSupport {

    private BrandingHttpSupport() {
    }

    public static String etag(long version) {
        return "\"brand-" + version + "\"";
    }

    public static boolean matches(String requestEtag, long version) {
        if (requestEtag == null || requestEtag.isBlank()) return false;
        String expected = etag(version);
        for (String candidate : requestEtag.split(",")) {
            String normalized = candidate.trim();
            if (normalized.equals("*") || normalized.equals(expected) || normalized.equals("W/" + expected)) {
                return true;
            }
        }
        return false;
    }

    public static Long parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return null;
        String normalized = ifMatch.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2);
        normalized = normalized.replace("\"", "");
        if (normalized.startsWith("brand-")) normalized = normalized.substring("brand-".length());
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("If-Match must contain a valid branding version");
        }
    }
}
