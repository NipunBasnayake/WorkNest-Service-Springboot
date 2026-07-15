package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.InterviewMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class InterviewScheduleRequestDto {

    @NotNull(message = "Application ID is required")
    private Long applicationId;

    private Long interviewerEmployeeId;

    @NotNull(message = "Interview scheduled time is required")
    private LocalDateTime scheduledAt;

    @NotNull(message = "Interview mode is required")
    private InterviewMode mode;

    @Size(max = 255, message = "Location must not exceed 255 characters")
    private String location;

    @Size(max = 1000, message = "Meeting link must not exceed 1000 characters")
    private String meetingLink;

    @Size(max = 5000, message = "Notes must not exceed 5000 characters")
    private String notes;
}
