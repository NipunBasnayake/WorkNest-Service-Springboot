package com.worknest.tenant.enums;

import com.worknest.common.exception.BadRequestException;

import java.util.Locale;

public enum RecruitmentReportType {
    JOB_OPENINGS,
    APPLICATIONS,
    INTERVIEWS,
    HIRING;

    public static RecruitmentReportType fromPath(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Recruitment report type is required");
        }
        try {
            return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported recruitment report type");
        }
    }
}
