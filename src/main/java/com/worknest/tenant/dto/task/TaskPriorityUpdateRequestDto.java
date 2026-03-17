package com.worknest.tenant.dto.task;

import com.worknest.tenant.enums.TaskPriority;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskPriorityUpdateRequestDto {

    @NotNull(message = "Task priority is required")
    private TaskPriority priority;
}
