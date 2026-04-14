package com.worknest.tenant.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectDocumentRequestDto {

    @NotBlank(message = "Document URL is required")
    @Size(max = 1000, message = "Document URL must not exceed 1000 characters")
    private String url;

    @Size(max = 255, message = "Document name must not exceed 255 characters")
    private String name;

    @Size(max = 120, message = "Document mime type must not exceed 120 characters")
    private String mimeType;

    private Long size;
}
