package com.worknest.tenant.dto.task;

import com.worknest.tenant.enums.TaskPriority;
import com.worknest.tenant.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TaskCreateRequestDto {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Task title is required")
    @Size(max = 180, message = "Task title must not exceed 180 characters")
    private String title;

    @Size(max = 4000, message = "Task description must not exceed 4000 characters")
    private String description;

    private TaskStatus status;

    private TaskPriority priority;

    @NotNull(message = "Assignee ID is required")
    private Long assigneeId;

    private Long createdByEmployeeId;

    private LocalDate dueDate;
}
