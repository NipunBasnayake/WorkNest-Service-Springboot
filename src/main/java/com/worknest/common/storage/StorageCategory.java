package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;

import java.util.List;
import java.util.Locale;

public enum StorageCategory {
    WORKSPACE_LOGO(List.of("workspace"), FileFamily.IMAGE),
    WORKSPACE_BANNER(List.of("workspace"), FileFamily.IMAGE),
    EMPLOYEE_AVATAR(List.of("employees"), FileFamily.IMAGE),
    TASK_ATTACHMENT(List.of("tasks"), FileFamily.ANY),
    ANNOUNCEMENT_ATTACHMENT(List.of("announcements"), FileFamily.ANY),
    LEAVE_ATTACHMENT(List.of("leave"), FileFamily.ANY),
    CHAT_ATTACHMENT(List.of("chat"), FileFamily.ANY),
    CANDIDATE_RESUME(List.of("recruitment", "resumes"), FileFamily.DOCUMENT),
    DOCUMENT(List.of("documents"), FileFamily.DOCUMENT);

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

    private enum FileFamily {
        IMAGE,
        DOCUMENT,
        ANY
    }
}