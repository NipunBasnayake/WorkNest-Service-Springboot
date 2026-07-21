package com.worknest.notification.email;

public record BrandContext(
        String companyName,
        String primaryColor) {

    public static BrandContext workNest() {
        return new BrandContext("WorkNest", "#9332EA");
    }
}
