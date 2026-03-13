package com.worknest.tenant.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worknest.common.api.ErrorResponse;
import com.worknest.common.util.AppConstants;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to extract and validate tenant ID from request header.
 * Sets tenant context for the current thread.
 * Validates tenant exists for tenant-specific endpoints.
 * Always clears context after request processing.
 */
@Component
@Order(1)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    private final MasterTenantLookupService masterTenantLookupService;
    private final ObjectMapper objectMapper;
    private final String tenantHeaderName;

    @Autowired
    public TenantContextFilter(
            MasterTenantLookupService masterTenantLookupService,
            ObjectMapper objectMapper,
            @Value("${app.tenant.header:" + AppConstants.TENANT_HEADER + "}") String tenantHeaderName) {
        this.masterTenantLookupService = masterTenantLookupService;
        this.objectMapper = objectMapper;
        this.tenantHeaderName = tenantHeaderName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String requestUri = request.getRequestURI();

            // Skip tenant validation for master endpoints
            if (isMasterEndpoint(requestUri)) {
                log.debug("Master endpoint accessed: {}", requestUri);
                filterChain.doFilter(request, response);
                return;
            }

            // Extract tenant ID from header
            String tenantId = request.getHeader(tenantHeaderName);

            // Validate tenant header for tenant endpoints
            if (tenantId == null || tenantId.trim().isEmpty()) {
                log.warn("Missing tenant header for tenant endpoint: {}", requestUri);
                sendErrorResponse(response, HttpStatus.BAD_REQUEST,
                        "Missing " + tenantHeaderName + " header", "MISSING_TENANT_HEADER", requestUri);
                return;
            }

            // Validate tenant exists
            if (masterTenantLookupService.findByTenantKey(tenantId).isEmpty()) {
                log.warn("Invalid tenant ID: {}", tenantId);
                sendErrorResponse(response, HttpStatus.NOT_FOUND,
                        "Tenant not found: " + tenantId, "TENANT_NOT_FOUND", requestUri);
                return;
            }

            // Set tenant context for this thread
            TenantContext.setTenantId(tenantId);
            log.debug("Tenant context set: {}", tenantId);

            // Continue with the request
            filterChain.doFilter(request, response);

        } finally {
            // Always clear tenant context after request completes
            TenantContext.clear();
            log.debug("Tenant context cleared");
        }
    }

    /**
     * Check if the request is for a master endpoint.
     * Master endpoints don't require tenant context.
     */
    private boolean isMasterEndpoint(String uri) {
        return uri.startsWith("/api/platform/") ||
               uri.startsWith("/actuator") ||
               uri.startsWith("/error") ||
               (uri.contains("/health") || uri.contains("/tenant-info"));
    }

    /**
     * Send JSON error response
     */
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status,
                                   String message, String errorCode, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(message, errorCode, path);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}

