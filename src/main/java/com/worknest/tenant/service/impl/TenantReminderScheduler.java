package com.worknest.tenant.service.impl;

import com.worknest.common.enums.TenantStatus;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import com.worknest.tenant.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TenantReminderScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TenantReminderScheduler.class);

    private final PlatformTenantRepository platformTenantRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final TenantReminderProcessorService tenantReminderProcessorService;

    public TenantReminderScheduler(
            PlatformTenantRepository platformTenantRepository,
            MasterTenantContextRunner masterTenantContextRunner,
            TenantReminderProcessorService tenantReminderProcessorService) {
        this.platformTenantRepository = platformTenantRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.tenantReminderProcessorService = tenantReminderProcessorService;
    }

    @Scheduled(
            cron = "${app.reminders.cron:0 0 */6 * * *}",
            zone = "${app.reminders.timezone:UTC}"
    )
    public void runReminderCycle() {
        List<String> activeTenantKeys = masterTenantContextRunner.runInMasterContext(() ->
                platformTenantRepository.findAll().stream()
                        .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                        .map(PlatformTenant::getTenantKey)
                        .toList()
        );

        for (String tenantKey : activeTenantKeys) {
            String normalizedTenantKey = normalizeTenantKey(tenantKey);
            if (normalizedTenantKey == null) {
                continue;
            }

            try {
                TenantContext.setTenantId(normalizedTenantKey);
                tenantReminderProcessorService.processTenantReminders(normalizedTenantKey);
            } catch (Exception ex) {
                logger.error("Reminder cycle failed for tenant {}", normalizedTenantKey, ex);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private String normalizeTenantKey(String tenantKey) {
        if (tenantKey == null) {
            return null;
        }
        String normalized = tenantKey.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}
