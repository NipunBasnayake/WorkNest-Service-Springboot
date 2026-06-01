package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.InterviewMode;
import com.worknest.tenant.enums.InterviewStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class InterviewResponseDto {
    private Long id;
    private Long applicationId;
    private CandidateResponseDto candidate;
    private JobPositionResponseDto jobPosition;
    private EmployeeSimpleDto interviewer;
    private InterviewMode mode;
    private InterviewStatus status;
    private LocalDateTime scheduledAt;
    private String location;
    private String meetingLink;
    private String notes;
    private InterviewFeedbackResponseDto feedback;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}