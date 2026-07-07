package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class CandidateApplicationResponseDto {
    private Long id;
    private CandidateResponseDto candidate;
    private JobPositionResponseDto jobPosition;
    private CandidatePipelineStatus status;
    private String coverLetter;
    private BigDecimal expectedSalary;
    private String recruiterNotes;
    private String rejectedReason;
    private EmployeeSimpleDto createdBy;
    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime offeredAt;
    private LocalDateTime hiredAt;
}