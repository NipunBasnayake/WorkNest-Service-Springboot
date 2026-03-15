package com.worknest.tenant.dto.analytics;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TaskProjectSummaryDto {
    private Long projectId;
    private String projectName;
    private long totalTasks;
}
