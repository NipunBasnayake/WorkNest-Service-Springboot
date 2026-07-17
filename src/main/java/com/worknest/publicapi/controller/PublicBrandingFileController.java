package com.worknest.publicapi.controller;

import com.worknest.common.storage.FileStorageService;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/public/{tenantSlug}/branding")
public class PublicBrandingFileController {
    private final FileStorageService fileStorageService;

    public PublicBrandingFileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> previewBrandingFile(@PathVariable("id") @Positive Long id) {
        FileStorageService.StoredFileResource file = fileStorageService.getPublicBrandingResource(id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.mimeType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''"
                        + UriUtils.encode(file.fileName(), StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(file.resource());
    }
}
