package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.audit.AuditLogResponseDto;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.service.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLogResponseDto>>> searchAuditLogs(
            @RequestParam(required = false) AuditActionType action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {

        List<AuditLogResponseDto> response = auditLogService.search(action, entityType, actorId, fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved successfully", response));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<PagedResultDto<AuditLogResponseDto>>> searchAuditLogsPaged(
            @RequestParam(required = false) AuditActionType action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PagedResultDto<AuditLogResponseDto> response = auditLogService.searchPaged(
                action,
                entityType,
                actorId,
                fromDate,
                toDate,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved successfully", response));
    }
}
