package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;

import java.util.List;
import java.util.Locale;

public enum StorageCategory {
    WORKSPACE_BANNER(List.of("company-logos"), FileFamily.IMAGE),
    EMPLOYEE_AVATAR(List.of("avatars"), FileFamily.IMAGE),
    IMAGE(List.of("future", "images"), FileFamily.IMAGE),
    PROJECT_ATTACHMENT(List.of("project-files"), FileFamily.ANY),
    TASK_ATTACHMENT(List.of("task-attachments"), FileFamily.ANY),
    ANNOUNCEMENT_ATTACHMENT(List.of("announcements"), FileFamily.ANY),
    LEAVE_ATTACHMENT(List.of("employee-documents", "leave"), FileFamily.ANY),
    CHAT_ATTACHMENT(List.of("chat-files"), FileFamily.ANY),
    CANDIDATE_RESUME(List.of("candidate-cvs"), FileFamily.DOCUMENT),
    OFFER_LETTER(List.of("offer-letters"), FileFamily.DOCUMENT),
    DOCUMENT(List.of("employee-documents"), FileFamily.DOCUMENT),
    TEMPORARY(List.of("temp"), FileFamily.ANY),
    FUTURE(List.of("future"), FileFamily.ANY);

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

    public static StorageCategory fromType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image" -> IMAGE;
            case "doc", "document" -> DOCUMENT;
            default -> throw new BadRequestException("Storage type must be 'image' or 'doc'");
        };
    }

    public static StorageCategory fromClientValue(String category, String folder, String type) {
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
        return fromType(type);
    }

    private enum FileFamily {
        IMAGE,
        DOCUMENT,
        ANY
    }
}
