package com.worknest.master.dto.subscription;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record TenantPlanAssignmentRequestDto(
        @NotBlank(message = "Subscription plan code is required")
        @Size(max = 50, message = "Subscription plan code must not exceed 50 characters")
        String planCode,

        @Future(message = "Subscription expiry must be in the future")
        LocalDateTime expiresAt) {
}
