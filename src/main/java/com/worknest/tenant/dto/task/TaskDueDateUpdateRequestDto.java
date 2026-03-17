package com.worknest.tenant.dto.task;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TaskDueDateUpdateRequestDto {

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;
}
