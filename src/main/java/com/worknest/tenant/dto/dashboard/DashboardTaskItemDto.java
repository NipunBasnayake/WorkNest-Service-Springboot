package com.worknest.tenant.dto.dashboard;

import com.worknest.tenant.enums.TaskPriority;
import com.worknest.tenant.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class DashboardTaskItemDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private String title;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate dueDate;
}
