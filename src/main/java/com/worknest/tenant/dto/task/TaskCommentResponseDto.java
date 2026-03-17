package com.worknest.tenant.dto.task;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TaskCommentResponseDto {
    private Long id;
    private Long taskId;
    private EmployeeSimpleDto commentedBy;
    private String comment;
    private LocalDateTime createdAt;
}
