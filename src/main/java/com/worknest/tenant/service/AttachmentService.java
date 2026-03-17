package com.worknest.tenant.service;

import com.worknest.tenant.dto.attachment.AttachmentResponseDto;
import com.worknest.tenant.enums.AttachmentEntityType;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService {

    AttachmentResponseDto uploadAttachment(AttachmentEntityType entityType, Long entityId, MultipartFile file);

    List<AttachmentResponseDto> listAttachments(AttachmentEntityType entityType, Long entityId);

    AttachmentDownloadResult downloadAttachment(Long attachmentId);

    void deleteAttachment(Long attachmentId);

    record AttachmentDownloadResult(Resource resource, String fileName, String mimeType) {}
}
