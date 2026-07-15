package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RecruitmentEmailLogResponseDto {
    private Long id;
    private RecruitmentEmailTemplateType templateType;
    private String recipientEmail;
    private String subject;
    private String deliveryStatus;
    private LocalDateTime sentAt;
}
