package com.worknest.tenant.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProjectTaskProgressDto {
    private Long projectId;
    private String projectName;
    private long totalTasks;
    private long doneTasks;
    private double completionPercent;
}
