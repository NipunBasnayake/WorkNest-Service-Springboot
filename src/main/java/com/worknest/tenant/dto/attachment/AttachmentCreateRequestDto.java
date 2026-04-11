package com.worknest.tenant.dto.attachment;

import com.worknest.tenant.enums.AttachmentEntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachmentCreateRequestDto {

    @NotNull(message = "Entity type is required")
    private AttachmentEntityType entityType;

    @NotNull(message = "Entity ID is required")
    @Positive(message = "Entity ID must be positive")
    private Long entityId;

    @NotBlank(message = "File URL is required")
    @Size(max = 1000, message = "File URL must not exceed 1000 characters")
    private String fileUrl;

    @NotBlank(message = "File name is required")
    @Size(max = 255, message = "File name must not exceed 255 characters")
    private String fileName;

    @NotBlank(message = "File type is required")
    @Size(max = 120, message = "File type must not exceed 120 characters")
    private String fileType;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSize;
}
