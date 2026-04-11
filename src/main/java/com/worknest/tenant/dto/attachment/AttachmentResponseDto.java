package com.worknest.tenant.dto.attachment;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.AttachmentEntityType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AttachmentResponseDto {
    private Long id;
    private AttachmentEntityType entityType;
    private Long entityId;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private String mimeType;
    private Long fileSize;
    private EmployeeSimpleDto uploadedBy;
    private LocalDateTime createdAt;
}
