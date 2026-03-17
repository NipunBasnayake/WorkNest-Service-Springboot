package com.worknest.tenant.dto.task;

import com.worknest.tenant.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class KanbanColumnDto {
    private TaskStatus status;
    private List<TaskResponseDto> tasks;
}
