package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.report.NotificationReportPageDto;
import com.worknest.tenant.service.NotificationReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/{tenantSlug}/reports/notifications")
public class NotificationReportController {

    private final NotificationReportService notificationReportService;

    public NotificationReportController(NotificationReportService notificationReportService) {
        this.notificationReportService = notificationReportService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationReportPageDto>> getReport(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "teamId", required = false) Long teamId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir,
            @RequestParam Map<String, String> requestParameters) {
        NotificationReportPageDto report = notificationReportService.getReport(
                search,
                fromDate,
                toDate,
                status,
                department,
                employeeId,
                projectId,
                teamId,
                requestParameters,
                page,
                size,
                sortBy,
                sortDir);
        return ResponseEntity.ok(ApiResponse.success("Notification report retrieved successfully", report));
    }
}
