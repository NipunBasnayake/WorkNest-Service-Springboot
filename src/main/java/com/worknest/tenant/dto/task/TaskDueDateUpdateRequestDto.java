package com.worknest.tenant.dto.task;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TaskDueDateUpdateRequestDto {

    private LocalDate dueDate;
}
