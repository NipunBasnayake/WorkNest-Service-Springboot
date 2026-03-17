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
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private EmployeeSimpleDto assignee;
    private EmployeeSimpleDto createdBy;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
