package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.CandidatePipelineStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RecruitmentStageCountDto {
    private CandidatePipelineStatus stage;
    private long count;
}