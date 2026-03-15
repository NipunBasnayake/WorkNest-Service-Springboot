package com.worknest.tenant.dto.task;

import com.worknest.tenant.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskStatusUpdateRequestDto {

    @NotNull(message = "Task status is required")
    private TaskStatus status;
}
