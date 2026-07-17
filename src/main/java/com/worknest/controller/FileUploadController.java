package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.common.storage.FileStorageService;
import com.worknest.common.storage.StorageCategory;
import com.worknest.common.storage.StoredFileDto;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/{tenantSlug}/files")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(value = {"", "/upload"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileStorageService.StoredFileResult> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "folder", required = false) String folder,
            @RequestParam(value = "type", required = false) String type) {
        StorageCategory storageCategory = StorageCategory.fromClientValue(category, folder, type);
        fileStorageService.validateUploadAccess(storageCategory);
        return ResponseEntity.ok(fileStorageService.storeForUpload(file, storageCategory));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoredFileDto>> getFile(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("File metadata retrieved successfully", fileStorageService.getFile(id)));
    }

    @GetMapping({"/{id}/preview", "/preview/{id}"})
    public ResponseEntity<Resource> previewFile(@PathVariable("id") @Positive Long id) {
        return resourceResponse(fileStorageService.getFileResource(id), true);
    }

    @GetMapping({"/{id}/download", "/download/{id}"})
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") @Positive Long id) {
        return resourceResponse(fileStorageService.getFileResource(id), false);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoredFileDto>> replaceFile(
            @PathVariable("id") @Positive Long id,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("File replaced successfully", fileStorageService.replace(id, file)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable("id") @Positive Long id) {
        fileStorageService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("File deleted successfully"));
    }

    private ResponseEntity<Resource> resourceResponse(FileStorageService.StoredFileResource file, boolean inline) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.mimeType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String disposition = (inline ? "inline" : "attachment") + "; filename*=UTF-8''"
                + UriUtils.encode(file.fileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(file.resource());
    }
}
