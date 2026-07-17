package com.worknest.publicapi.listener;

import com.worknest.multitenancy.context.TenantContextHolder;
import com.worknest.publicapi.event.PublicApplicationSubmittedEvent;
import com.worknest.publicapi.service.PublicApplicationConfirmationEmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PublicApplicationSubmittedEmailListenerTest {

    private final PublicApplicationConfirmationEmailService emailService =
            mock(PublicApplicationConfirmationEmailService.class);
    private final PublicApplicationSubmittedEmailListener listener =
            new PublicApplicationSubmittedEmailListener(emailService);

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void emailFailureDoesNotEscapeAfterApplicationCommit() {
        PublicApplicationSubmittedEvent event = event();
        doThrow(new IllegalStateException("mail template store unavailable"))
                .when(emailService)
                .queueApplicationReceivedEmail(40L, "Residue Solutions", "https://worknest.test/careers");

        assertThatCode(() -> listener.onApplicationSubmitted(event)).doesNotThrowAnyException();
        assertThat(TenantContextHolder.getTenantKey()).isNull();
        assertThat(TenantContextHolder.getTenantSlug()).isNull();
    }

    @Test
    void usesEventTenantAndRestoresPreviousContext() {
        TenantContextHolder.setTenantKey("previous-key");
        TenantContextHolder.setTenantSlug("previous-slug");

        listener.onApplicationSubmitted(event());

        verify(emailService).queueApplicationReceivedEmail(
                40L, "Residue Solutions", "https://worknest.test/careers");
        assertThat(TenantContextHolder.getTenantKey()).isEqualTo("previous-key");
        assertThat(TenantContextHolder.getTenantSlug()).isEqualTo("previous-slug");
    }

    private PublicApplicationSubmittedEvent event() {
        return new PublicApplicationSubmittedEvent(
                40L,
                "tenant-residue",
                "residue-solutions",
                "Residue Solutions",
                "https://worknest.test/careers");
    }
}
