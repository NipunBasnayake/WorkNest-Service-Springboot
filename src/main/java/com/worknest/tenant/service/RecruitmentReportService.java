package com.worknest.tenant.service;

import com.worknest.tenant.dto.report.RecruitmentReportPageDto;
import com.worknest.tenant.enums.RecruitmentReportType;

import java.time.LocalDate;
import java.util.Map;

public interface RecruitmentReportService {
    RecruitmentReportPageDto getReport(
            RecruitmentReportType type,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            String department,
            Map<String, String> columnFilters,
            int page,
            int size,
            String sortBy,
            String sortDir);
}
