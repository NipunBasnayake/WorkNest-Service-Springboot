package com.worknest.tenant.dto.report;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RecruitmentReportPageDto {
    private List<Map<String, Object>> rows;
    private Map<String, Long> summary;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private LocalDateTime generatedAt;
}
