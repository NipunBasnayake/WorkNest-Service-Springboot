package com.worknest.tenant.dto.recruitment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecruitmentEmailTemplateUpdateRequestDto {
    @NotBlank(message = "Email subject is required")
    @Size(max = 240, message = "Email subject must not exceed 240 characters")
    private String subject;

    @NotBlank(message = "Email body is required")
    @Size(max = 12000, message = "Email body must not exceed 12000 characters")
    private String bodyMarkdown;

    private Boolean enabled;
}
