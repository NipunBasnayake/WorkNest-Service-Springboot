package com.worknest.tenant.dto.analytics;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TaskAssigneeSummaryDto {
    private EmployeeSimpleDto assignee;
    private long totalTasks;
    private long overdueTasks;
}
