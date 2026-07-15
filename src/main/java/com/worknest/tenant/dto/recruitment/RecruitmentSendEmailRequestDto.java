package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecruitmentSendEmailRequestDto {
    @NotNull(message = "Email template type is required")
    private RecruitmentEmailTemplateType templateType;
}
