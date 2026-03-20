package com.worknest.tenant.dto.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnnouncementCreateRequestDto {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Message is required")
    @Size(max = 5000, message = "Message must not exceed 5000 characters")
    private String message;

    @Positive(message = "Creator employee ID must be positive")
    private Long createdByEmployeeId;

    @Positive(message = "Team ID must be positive")
    private Long teamId;
}
