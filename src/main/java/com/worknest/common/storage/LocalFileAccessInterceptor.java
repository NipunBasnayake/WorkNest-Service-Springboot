package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component
public class LocalFileAccessInterceptor implements HandlerInterceptor {

    private static final String FILES_PREFIX = "/files/";

    private final FileStorageService fileStorageService;

    public LocalFileAccessInterceptor(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            throw new BadRequestException("Files can only be downloaded with GET or HEAD requests");
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        path = UriUtils.decode(path, StandardCharsets.UTF_8);
        if (!path.startsWith(FILES_PREFIX)) {
            return true;
        }

        String remainder = path.substring(FILES_PREFIX.length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex <= 0 || slashIndex == remainder.length() - 1) {
            throw new BadRequestException("Invalid file URL");
        }

        String tenantSlug = remainder.substring(0, slashIndex);
        String relativePath = remainder.substring(slashIndex + 1);
        fileStorageService.validateTenantFileAccess(tenantSlug, relativePath);
        return true;
    }
}