package com.worknest.tenant.dto.project;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ProjectDetailResponseDto {
    private ProjectResponseDto project;
    private List<ProjectTeamResponseDto> teams;
}
