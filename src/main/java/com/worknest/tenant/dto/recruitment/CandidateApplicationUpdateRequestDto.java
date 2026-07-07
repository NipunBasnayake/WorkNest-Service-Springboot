package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.CandidatePipelineStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CandidateApplicationUpdateRequestDto {

    @NotNull(message = "Candidate pipeline status is required")
    private CandidatePipelineStatus status;

    @Size(max = 5000, message = "Recruiter notes must not exceed 5000 characters")
    private String recruiterNotes;

    @Size(max = 5000, message = "Rejected reason must not exceed 5000 characters")
    private String rejectedReason;

    private BigDecimal expectedSalary;
}