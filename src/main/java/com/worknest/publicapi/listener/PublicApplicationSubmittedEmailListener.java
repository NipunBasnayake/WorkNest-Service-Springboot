package com.worknest.publicapi.listener;

import com.worknest.multitenancy.context.TenantContextHolder;
import com.worknest.publicapi.event.PublicApplicationSubmittedEvent;
import com.worknest.publicapi.service.PublicApplicationConfirmationEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PublicApplicationSubmittedEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PublicApplicationSubmittedEmailListener.class);

    private final PublicApplicationConfirmationEmailService confirmationEmailService;

    public PublicApplicationSubmittedEmailListener(
            PublicApplicationConfirmationEmailService confirmationEmailService) {
        this.confirmationEmailService = confirmationEmailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationSubmitted(PublicApplicationSubmittedEvent event) {
        String previousTenantKey = TenantContextHolder.getTenantKey();
        String previousTenantSlug = TenantContextHolder.getTenantSlug();
        try {
            TenantContextHolder.setTenantKey(event.tenantKey());
            TenantContextHolder.setTenantSlug(event.tenantSlug());
            confirmationEmailService.queueApplicationReceivedEmail(
                    event.applicationId(),
                    event.companyName(),
                    event.careersLink());
        } catch (RuntimeException ex) {
            log.error(
                    "Application {} was saved, but its confirmation email could not be queued for tenant {}",
                    event.applicationId(),
                    event.tenantSlug(),
                    ex);
        } finally {
            restoreTenantContext(previousTenantKey, previousTenantSlug);
        }
    }

    private void restoreTenantContext(String tenantKey, String tenantSlug) {
        TenantContextHolder.clear();
        if (tenantKey != null && !tenantKey.isBlank()) {
            TenantContextHolder.setTenantKey(tenantKey);
        }
        if (tenantSlug != null && !tenantSlug.isBlank()) {
            TenantContextHolder.setTenantSlug(tenantSlug);
        }
    }
}
