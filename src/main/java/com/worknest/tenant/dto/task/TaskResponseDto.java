package com.worknest.tenant.dto.task;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.TaskPriority;
import com.worknest.tenant.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TaskResponseDto {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long assignedTeamId;
    private String assignedTeamName;
    private Long assignedEmployeeId;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private EmployeeSimpleDto assignee;
    private EmployeeSimpleDto createdBy;
    private Long createdByEmployeeId;
    private Long createdByUserId;
    private EmployeeSimpleDto assignedBy;
    private Long assignedByEmployeeId;
    private Long assignedByUserId;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
