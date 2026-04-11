package com.worknest.tenant.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskCommentCreateRequestDto {

    @Deprecated
    @Positive(message = "Commenter employee ID must be positive")
    private Long commentedByEmployeeId;

    @NotBlank(message = "Comment is required")
    @Size(max = 3000, message = "Comment must not exceed 3000 characters")
    private String comment;
}
