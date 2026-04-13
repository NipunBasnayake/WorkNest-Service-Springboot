package com.worknest.tenant.dto.task;

import com.worknest.tenant.enums.TaskPriority;
import com.worknest.tenant.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TaskCreateRequestDto {

    @NotNull(message = "Project ID is required")
    @Positive(message = "Project ID must be positive")
    private Long projectId;

    @NotBlank(message = "Task title is required")
    @Size(max = 180, message = "Task title must not exceed 180 characters")
    private String title;

    @Size(max = 4000, message = "Task description must not exceed 4000 characters")
    private String description;

    private TaskStatus status;

    private TaskPriority priority;

    @NotNull(message = "Assigned team ID is required")
    @Positive(message = "Assigned team ID must be positive")
    private Long assignedTeamId;

    @Positive(message = "Assigned employee ID must be positive")
    private Long assignedEmployeeId;

    @Deprecated
    @Positive(message = "Assignee ID must be positive")
    private Long assigneeId;

    @Deprecated
    @Positive(message = "Creator employee ID must be positive")
    private Long createdByEmployeeId;

    private LocalDate dueDate;
}
