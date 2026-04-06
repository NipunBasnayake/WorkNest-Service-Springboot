package com.worknest.tenant.dto.task;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskAssigneeUpdateRequestDto {

    @NotNull(message = "Assignee ID is required")
    private Long assigneeId;
}
