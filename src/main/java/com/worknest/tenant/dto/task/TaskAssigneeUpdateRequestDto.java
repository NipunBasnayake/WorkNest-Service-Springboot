package com.worknest.tenant.dto.task;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskAssigneeUpdateRequestDto {

    @Positive(message = "Assigned employee ID must be positive")
    private Long assignedEmployeeId;

    @Positive(message = "Assignee ID must be positive")
    private Long assigneeId;
}
