package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.CandidatePipelineStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CandidateApplicationCreateRequestDto {

    @NotNull(message = "Candidate ID is required")
    private Long candidateId;

    @NotNull(message = "Job position ID is required")
    private Long jobPositionId;

    @Size(max = 5000, message = "Cover letter must not exceed 5000 characters")
    private String coverLetter;

    private BigDecimal expectedSalary;

    @Size(max = 120, message = "Source must not exceed 120 characters")
    private String source;

    private CandidatePipelineStatus status;
}