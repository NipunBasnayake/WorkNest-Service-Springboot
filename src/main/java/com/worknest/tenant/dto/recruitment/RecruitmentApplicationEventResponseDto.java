package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RecruitmentApplicationEventResponseDto {
    private Long id;
    private String eventType;
    private String title;
    private String detail;
    private EmployeeSimpleDto actor;
    private LocalDateTime occurredAt;
}
