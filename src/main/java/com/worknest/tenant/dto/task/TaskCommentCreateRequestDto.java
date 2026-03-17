package com.worknest.tenant.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskCommentCreateRequestDto {

    @NotNull(message = "Commenter employee ID is required")
    private Long commentedByEmployeeId;

    @NotBlank(message = "Comment is required")
    @Size(max = 3000, message = "Comment must not exceed 3000 characters")
    private String comment;
}
