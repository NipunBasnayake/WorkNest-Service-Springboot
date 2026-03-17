package com.worknest.tenant.dto.team;

import com.worknest.tenant.enums.ProjectStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class TeamProjectSummaryDto {
    private Long projectId;
    private String projectName;
    private ProjectStatus projectStatus;
    private LocalDate startDate;
    private LocalDate endDate;
}
