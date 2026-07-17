package com.worknest.publicapi.event;

public record PublicApplicationSubmittedEvent(
        Long applicationId,
        String tenantKey,
        String tenantSlug,
        String companyName,
        String careersLink) {
}
