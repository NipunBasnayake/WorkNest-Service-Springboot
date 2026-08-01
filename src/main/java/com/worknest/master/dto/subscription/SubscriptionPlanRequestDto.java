package com.worknest.master.dto.subscription;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SubscriptionPlanRequestDto(
        @NotBlank(message = "Plan name is required")
        @Size(max = 100, message = "Plan name must not exceed 100 characters")
        String name,

        @NotBlank(message = "Plan code is required")
        @Size(max = 50, message = "Plan code must not exceed 50 characters")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$", message = "Plan code must contain only letters, numbers, hyphens, and underscores")
        String code,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @PositiveOrZero(message = "Monthly price must be zero or greater")
        BigDecimal monthlyPrice,

        @PositiveOrZero(message = "Yearly price must be zero or greater")
        BigDecimal yearlyPrice,

        @Size(max = 30, message = "Billing period must not exceed 30 characters")
        String billingPeriod,

        @Size(max = 60, message = "Badge must not exceed 60 characters")
        String badge,

        Boolean recommended,

        @Size(max = 30, message = "Color must not exceed 30 characters")
        String color,

        @Size(max = 60, message = "Icon must not exceed 60 characters")
        String icon,

        @NotNull(message = "Active status is required")
        Boolean active,

        @NotNull(message = "Display order is required")
        @Min(value = 0, message = "Display order must be zero or greater")
        @Max(value = 10000, message = "Display order must not exceed 10000")
        Integer displayOrder) {
}
