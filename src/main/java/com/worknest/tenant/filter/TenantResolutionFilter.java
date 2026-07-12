package com.worknest.tenant.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worknest.common.api.ErrorResponse;
import com.worknest.common.enums.TenantStatus;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.multitenancy.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@Order(1)
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TenantResolutionFilter.class);

    private static final String TENANT_SPOOF_ERROR = "TENANT_SPOOF_DETECTED";

    private final MasterTenantLookupService masterTenantLookupService;
    private final ObjectMapper objectMapper;
    private final String tenantHeaderName;

    public TenantResolutionFilter(
            MasterTenantLookupService masterTenantLookupService,
            ObjectMapper objectMapper,
            @Value("${app.tenant.header:X-Tenant-Slug}") String tenantHeaderName) {
        this.masterTenantLookupService = masterTenantLookupService;
        this.objectMapper = objectMapper;
        this.tenantHeaderName = tenantHeaderName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String requestUri = request.getRequestURI();
            String traceId = resolveTraceId(request);
            request.setAttribute("traceId", traceId);
            MDC.put("traceId", traceId);

            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                filterChain.doFilter(request, response);
                return;
            }

            String tenantSlug = resolveTenantSlugFromRequest(requestUri);
            log.debug("[TENANT] Resolved slug={}", tenantSlug);
            String headerTenant = normalizeTenantSlug(request.getHeader(tenantHeaderName));

            if (tenantSlug == null) {
                if (isTenantApi(requestUri) || isPublicCareerApi(requestUri)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                TenantContextHolder.clear();
                filterChain.doFilter(request, response);
                return;
            }

            PlatformTenant tenant = masterTenantLookupService.findBySlug(tenantSlug).orElse(null);
            if (tenant == null) {
                sendErrorResponse(response, HttpStatus.NOT_FOUND,
                        "Tenant not found: " + tenantSlug, "TENANT_NOT_FOUND", requestUri);
                return;
            }

            if (headerTenant != null && !tenantHeaderMatches(tenant, headerTenant)) {
                sendErrorResponse(response, HttpStatus.FORBIDDEN,
                        "Tenant mismatch between URL and header", TENANT_SPOOF_ERROR, requestUri);
                return;
            }

            if (tenant.getStatus() != TenantStatus.ACTIVE || Boolean.FALSE.equals(tenant.getActive())) {
                sendErrorResponse(response, HttpStatus.FORBIDDEN,
                        "Tenant is not active: " + tenantSlug, "TENANT_INACTIVE", requestUri);
                return;
            }

            TenantContextHolder.setTenantSlug(tenant.getSlug());
            TenantContextHolder.setTenantKey(tenant.getTenantKey());
            MDC.put("tenantId", tenant.getTenantKey());
            MDC.put("tenantSlug", tenant.getSlug());
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove("tenantId");
            MDC.remove("tenantSlug");
            MDC.remove("traceId");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/auth/")
                || uri.startsWith("/api/platform/")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/actuator")
                || uri.startsWith("/error");
    }

    private boolean isTenantApi(String uri) {
        return uri.startsWith("/api/") && !uri.startsWith("/api/auth/") && !uri.startsWith("/api/public/") && !uri.startsWith("/api/platform/");
    }

    private boolean isPublicCareerApi(String uri) {
        return uri.startsWith("/api/public/");
    }

    private String resolveTenantSlugFromRequest(String uri) {
        if (uri == null) {
            return null;
        }

        if (uri.startsWith("/api/public/")) {
            return segmentAfterPrefix(uri, "/api/public/");
        }

        if (uri.startsWith("/api/")) {
            String remainder = uri.substring("/api/".length());
            if (remainder.startsWith("auth/") || remainder.startsWith("platform/") || remainder.startsWith("public/")) {
                return null;
            }
            return segmentAfterPrefix(uri, "/api/");
        }

        return null;
    }

    private String segmentAfterPrefix(String uri, String prefix) {
        String remainder = uri.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        String candidate = slashIndex >= 0 ? remainder.substring(0, slashIndex) : remainder;
        return normalizeTenantSlug(candidate);
    }

    private String normalizeTenantSlug(String tenantSlug) {
        if (tenantSlug == null) {
            return null;
        }
        String normalized = tenantSlug.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean tenantHeaderMatches(PlatformTenant tenant, String headerTenant) {
        String normalizedHeader = normalizeTenantSlug(headerTenant);
        String tenantKey = normalizeTenantSlug(tenant.getTenantKey());
        String tenantSlug = normalizeTenantSlug(tenant.getSlug());
        return normalizedHeader != null
                && (normalizedHeader.equalsIgnoreCase(tenantKey)
                || normalizedHeader.equalsIgnoreCase(tenantSlug));
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status,
                                   String message, String errorCode, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(
                status,
                errorCode,
                message,
                path,
                UUID.randomUUID().toString(),
                List.of()
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId != null && !traceId.isBlank()) {
            return traceId.trim();
        }
        return UUID.randomUUID().toString();
    }
}
