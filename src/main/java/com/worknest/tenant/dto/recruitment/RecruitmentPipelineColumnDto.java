package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.CandidatePipelineStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class RecruitmentPipelineColumnDto {
    private CandidatePipelineStatus stage;
    private String label;
    private long count;
    private List<CandidateApplicationResponseDto> applications;
}