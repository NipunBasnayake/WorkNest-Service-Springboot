package com.worknest.tenant.dto.leave;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeaveAttachmentRequestDto {

    @NotBlank(message = "Attachment file URL is required")
    @Size(max = 1000, message = "Attachment file URL must not exceed 1000 characters")
    private String fileUrl;

    @NotBlank(message = "Attachment file name is required")
    @Size(max = 255, message = "Attachment file name must not exceed 255 characters")
    private String fileName;

    @NotBlank(message = "Attachment file type is required")
    @Size(max = 120, message = "Attachment file type must not exceed 120 characters")
    private String fileType;

    @NotNull(message = "Attachment file size is required")
    @Positive(message = "Attachment file size must be positive")
    private Long fileSize;
}