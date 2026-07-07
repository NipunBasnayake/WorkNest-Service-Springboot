package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.InterviewRecommendation;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class InterviewFeedbackResponseDto {
    private Long id;
    private Long interviewId;
    private EmployeeSimpleDto reviewer;
    private Integer rating;
    private InterviewRecommendation recommendation;
    private String strengths;
    private String concerns;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}