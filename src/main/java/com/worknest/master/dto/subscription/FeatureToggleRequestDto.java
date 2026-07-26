package com.worknest.master.dto.subscription;

import jakarta.validation.constraints.NotNull;

public record FeatureToggleRequestDto(
        @NotNull(message = "Enabled status is required")
        Boolean enabled) {
}
