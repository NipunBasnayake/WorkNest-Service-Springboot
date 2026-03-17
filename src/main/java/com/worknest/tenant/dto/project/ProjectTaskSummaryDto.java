package com.worknest.tenant.dto.project;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProjectTaskSummaryDto {
    private long totalTasks;
    private long todoTasks;
    private long inProgressTasks;
    private long inReviewTasks;
    private long doneTasks;
    private long blockedTasks;
    private long overdueTasks;
    private double completionRatePercent;
}
