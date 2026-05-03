package com.worknest.tenant.dto.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnnouncementUpdateRequestDto {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 5000, message = "Content must not exceed 5000 characters")
    private String content;

    @Deprecated
    @Size(max = 5000, message = "Message must not exceed 5000 characters")
    private String message;

    private boolean pinned;

    public String resolveContent() {
        return content != null ? content : message;
    }
}
