package com.worknest.tenant.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class ManagerDashboardDto {
    private Long managerEmployeeId;
    private long managedTeams;
    private long managedProjects;
    private long totalProjectTasks;
    private long overdueProjectTasks;
    private List<StatusCountDto> tasksByStatus;
    private List<ProjectTaskProgressDto> projectTaskProgress;
    private LocalDateTime generatedAt;
}
