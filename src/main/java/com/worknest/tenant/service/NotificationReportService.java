package com.worknest.tenant.service;

import com.worknest.tenant.dto.report.NotificationReportPageDto;

import java.time.LocalDate;
import java.util.Map;

public interface NotificationReportService {
    NotificationReportPageDto getReport(
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            String department,
            Long employeeId,
            Long projectId,
            Long teamId,
            Map<String, String> columnFilters,
            int page,
            int size,
            String sortBy,
            String sortDir);
}
