package com.worknest.master.dto.subscription;

import jakarta.validation.constraints.NotNull;

public record PlanActiveRequestDto(
        @NotNull(message = "Active status is required")
        Boolean active) {
}
