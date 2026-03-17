package com.worknest.tenant.dto.project;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProjectTeamResponseDto {
    private Long id;
    private Long projectId;
    private Long teamId;
    private String teamName;
}
