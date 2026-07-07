package com.worknest.tenant.dto.recruitment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CandidateCommentCreateRequestDto {

    @NotNull(message = "Candidate ID is required")
    private Long candidateId;

    @NotBlank(message = "Comment message is required")
    @Size(max = 5000, message = "Comment message must not exceed 5000 characters")
    private String message;
}