package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.report.RecruitmentReportPageDto;
import com.worknest.tenant.enums.RecruitmentReportType;
import com.worknest.tenant.service.RecruitmentReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/{tenantSlug}/reports/recruitment")
public class RecruitmentReportController {

    private final RecruitmentReportService recruitmentReportService;

    public RecruitmentReportController(RecruitmentReportService recruitmentReportService) {
        this.recruitmentReportService = recruitmentReportService;
    }

    @GetMapping("/{reportType}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RecruitmentReportPageDto>> getReport(
            @PathVariable("reportType") String reportType,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir,
            @RequestParam Map<String, String> requestParameters) {
        RecruitmentReportPageDto report = recruitmentReportService.getReport(
                RecruitmentReportType.fromPath(reportType),
                search,
                fromDate,
                toDate,
                status,
                department,
                requestParameters,
                page,
                size,
                sortBy,
                sortDir);
        return ResponseEntity.ok(ApiResponse.success("Recruitment report retrieved successfully", report));
    }
}
