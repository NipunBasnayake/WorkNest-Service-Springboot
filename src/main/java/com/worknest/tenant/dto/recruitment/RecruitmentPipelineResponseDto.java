package com.worknest.tenant.dto.recruitment;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class RecruitmentPipelineResponseDto {
    private List<RecruitmentPipelineColumnDto> columns;
}