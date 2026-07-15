package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class RecruitmentEmailTemplateResponseDto {
    private Long id;
    private RecruitmentEmailTemplateType type;
    private String subject;
    private String bodyMarkdown;
    private boolean enabled;
    private List<String> availableVariables;
    private LocalDateTime updatedAt;
}
