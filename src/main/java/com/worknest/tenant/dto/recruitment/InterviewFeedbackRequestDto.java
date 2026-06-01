package com.worknest.tenant.dto.recruitment;

import com.worknest.tenant.enums.InterviewRecommendation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InterviewFeedbackRequestDto {

    @NotNull(message = "Interview ID is required")
    private Long interviewId;

    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    @NotNull(message = "Recommendation is required")
    private InterviewRecommendation recommendation;

    @Size(max = 5000, message = "Strengths must not exceed 5000 characters")
    private String strengths;

    @Size(max = 5000, message = "Concerns must not exceed 5000 characters")
    private String concerns;

    @Size(max = 5000, message = "Notes must not exceed 5000 characters")
    private String notes;
}