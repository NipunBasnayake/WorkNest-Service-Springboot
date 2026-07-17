package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;

import java.util.List;
import java.util.Locale;

public enum StorageCategory {
    WORKSPACE_LOGO(List.of("companies", "logos"), FileFamily.IMAGE),
    WORKSPACE_BANNER(List.of("companies", "banners"), FileFamily.IMAGE),
    EMPLOYEE_AVATAR(List.of("employees", "photos"), FileFamily.IMAGE),
    PROJECT_ATTACHMENT(List.of("projects", "attachments"), FileFamily.ANY),
    TASK_ATTACHMENT(List.of("tasks", "attachments"), FileFamily.ANY),
    ANNOUNCEMENT_ATTACHMENT(List.of("announcements", "attachments"), FileFamily.ANY),
    LEAVE_ATTACHMENT(List.of("leave", "attachments"), FileFamily.ANY),
    CHAT_ATTACHMENT(List.of("chat", "attachments"), FileFamily.ANY),
    CANDIDATE_RESUME(List.of("recruitment", "resumes"), FileFamily.DOCUMENT),
    DOCUMENT(List.of("documents"), FileFamily.DOCUMENT),
    TEMPORARY(List.of("temporary"), FileFamily.ANY);

    private final List<String> directorySegments;
    private final FileFamily fileFamily;

    StorageCategory(List<String> directorySegments, FileFamily fileFamily) {
        this.directorySegments = List.copyOf(directorySegments);
        this.fileFamily = fileFamily;
    }

    public List<String> directorySegments() {
        return directorySegments;
    }

    public boolean acceptsImage() {
        return fileFamily == FileFamily.IMAGE || fileFamily == FileFamily.ANY;
    }

    public boolean acceptsDocument() {
        return fileFamily == FileFamily.DOCUMENT || fileFamily == FileFamily.ANY;
    }

    public static StorageCategory fromLegacyType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image" -> EMPLOYEE_AVATAR;
            case "doc", "document" -> DOCUMENT;
            default -> throw new BadRequestException("Storage type must be 'image' or 'doc'");
        };
    }

    public static StorageCategory fromClientValue(String category, String folder, String legacyType) {
        String normalizedCategory = category == null ? "" : category.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if (!normalizedCategory.isBlank()) {
            try {
                return valueOf(normalizedCategory);
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Unsupported storage category");
            }
        }
        String normalizedFolder = folder == null ? "" : folder.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalizedFolder.startsWith("projects/")) return PROJECT_ATTACHMENT;
        if (normalizedFolder.startsWith("tasks/")) return TASK_ATTACHMENT;
        if (normalizedFolder.startsWith("announcements/")) return ANNOUNCEMENT_ATTACHMENT;
        if (normalizedFolder.startsWith("leave/")) return LEAVE_ATTACHMENT;
        if (normalizedFolder.startsWith("chat/")) return CHAT_ATTACHMENT;
        if (normalizedFolder.startsWith("recruitment/")) return CANDIDATE_RESUME;
        if (normalizedFolder.contains("avatar") || normalizedFolder.startsWith("employees/")) return EMPLOYEE_AVATAR;
        if (normalizedFolder.contains("logo")) return WORKSPACE_LOGO;
        return fromLegacyType(legacyType);
    }

    private enum FileFamily {
        IMAGE,
        DOCUMENT,
        ANY
    }
}
