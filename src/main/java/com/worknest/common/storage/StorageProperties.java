package com.worknest.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String root = "uploads";
    private DataSize maxImageSize = DataSize.ofMegabytes(2);
    private DataSize maxDocumentSize = DataSize.ofMegabytes(10);
    private List<String> allowedImageTypes = new ArrayList<>(List.of("jpg", "jpeg", "png", "webp"));
    private List<String> allowedDocumentTypes = new ArrayList<>(List.of("pdf", "docx", "xlsx", "pptx", "zip"));

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public DataSize getMaxImageSize() {
        return maxImageSize;
    }

    public void setMaxImageSize(DataSize maxImageSize) {
        this.maxImageSize = maxImageSize;
    }

    public DataSize getMaxDocumentSize() {
        return maxDocumentSize;
    }

    public void setMaxDocumentSize(DataSize maxDocumentSize) {
        this.maxDocumentSize = maxDocumentSize;
    }

    public List<String> getAllowedImageTypes() {
        return allowedImageTypes;
    }

    public void setAllowedImageTypes(List<String> allowedImageTypes) {
        this.allowedImageTypes = allowedImageTypes;
    }

    public List<String> getAllowedDocumentTypes() {
        return allowedDocumentTypes;
    }

    public void setAllowedDocumentTypes(List<String> allowedDocumentTypes) {
        this.allowedDocumentTypes = allowedDocumentTypes;
    }

    public Path rootPath() {
        return Paths.get(root).toAbsolutePath().normalize();
    }

    public long maxImageSizeBytes() {
        return maxImageSize.toBytes();
    }

    public long maxDocumentSizeBytes() {
        return maxDocumentSize.toBytes();
    }
}