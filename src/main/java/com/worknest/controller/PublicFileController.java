package com.worknest.controller;

import com.worknest.common.storage.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
public class PublicFileController {

    private final FileStorageService fileStorageService;

    public PublicFileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/api/public/{tenantSlug}/files/{folderName}/{fileName:.+}")
    public ResponseEntity<Resource> downloadSignedFile(
            @PathVariable("tenantSlug") String tenantSlug,
            @PathVariable("folderName") String folderName,
            @PathVariable("fileName") String fileName,
            @RequestParam("expires") long expires,
            @RequestParam("signature") String signature) {
        FileStorageService.StoredFileResource result = fileStorageService.loadPublicResource(
                tenantSlug,
                folderName,
                fileName,
                expires,
                signature
        );

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (result.mimeType() != null && !result.mimeType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(result.mimeType());
            } catch (IllegalArgumentException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        String encodedFileName = UriUtils.encode(result.fileName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
                .contentType(mediaType)
                .body(result.resource());
    }
}
