package com.worknest.tenant.dto.recruitment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecruitmentApplicationNoteRequestDto {
    @NotBlank(message = "Note is required")
    @Size(max = 5000, message = "Note must not exceed 5000 characters")
    private String message;
}
