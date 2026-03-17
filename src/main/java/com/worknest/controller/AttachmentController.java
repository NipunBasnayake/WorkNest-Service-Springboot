package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.attachment.AttachmentResponseDto;
import com.worknest.tenant.enums.AttachmentEntityType;
import com.worknest.tenant.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AttachmentResponseDto>> uploadAttachment(
            @RequestParam AttachmentEntityType entityType,
            @RequestParam Long entityId,
            @RequestPart("file") MultipartFile file) {
        AttachmentResponseDto response = attachmentService.uploadAttachment(entityType, entityId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Attachment uploaded successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<AttachmentResponseDto>>> listAttachments(
            @RequestParam AttachmentEntityType entityType,
            @RequestParam Long entityId) {
        List<AttachmentResponseDto> response = attachmentService.listAttachments(entityType, entityId);
        return ResponseEntity.ok(ApiResponse.success("Attachments retrieved successfully", response));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        AttachmentService.AttachmentDownloadResult result = attachmentService.downloadAttachment(id);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (result.mimeType() != null && !result.mimeType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(result.mimeType());
            } catch (IllegalArgumentException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        String encodedFileName = UriUtils.encode(result.fileName(), StandardCharsets.UTF_8);
        String contentDisposition = "attachment; filename*=UTF-8''" + encodedFileName;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(mediaType)
                .body(result.resource());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(@PathVariable Long id) {
        attachmentService.deleteAttachment(id);
        return ResponseEntity.ok(ApiResponse.success("Attachment deleted successfully"));
    }
}
