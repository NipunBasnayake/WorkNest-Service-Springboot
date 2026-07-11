package com.worknest.master.service.impl;

import com.worknest.common.enums.TenantStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.master.dto.PlatformTenantResponseDto;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.entity.PlatformTenantStatusAudit;
import com.worknest.master.repository.PlatformTenantRepository;
import com.worknest.master.repository.PlatformTenantStatusAuditRepository;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformTenantServiceImplTest {
    private PlatformTenantRepository tenantRepository;
    private PlatformTenantStatusAuditRepository auditRepository;
    private PlatformTenantServiceImpl service;
    private PlatformTenant tenant;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(PlatformTenantRepository.class);
        auditRepository = mock(PlatformTenantStatusAuditRepository.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUserEmailOrThrow()).thenReturn("platform.admin@worknest.test");

        tenant = new PlatformTenant();
        tenant.setId(10L);
        tenant.setTenantKey("acme");
        tenant.setSlug("acme");
        tenant.setCompanyName("Acme Ltd");
        tenant.setDatabaseName("tenant_acme");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setActive(true);
        tenant.setCreatedAt(LocalDateTime.now().minusDays(5));
        tenant.setUpdatedAt(LocalDateTime.now().minusDays(1));

        when(tenantRepository.findByTenantKey("acme")).thenReturn(Optional.of(tenant));
        when(tenantRepository.saveAndFlush(any(PlatformTenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRepository.saveAndFlush(any(PlatformTenantStatusAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = new PlatformTenantServiceImpl(
                tenantRepository,
                new ModelMapper(),
                new MasterTenantContextRunner("master"),
                auditRepository,
                securityUtils
        );
    }

    @Test
    void supportsAllRequiredStatusTransitionsAndKeepsLegacyFlagSynchronized() {
        List<TenantStatus> transitions = List.of(
                TenantStatus.SUSPENDED,
                TenantStatus.ACTIVE,
                TenantStatus.INACTIVE,
                TenantStatus.ACTIVE
        );

        for (TenantStatus next : transitions) {
            LocalDateTime previousUpdate = tenant.getUpdatedAt();
            PlatformTenantResponseDto response = service.updateTenantStatus(" ACME ", next);
            assertEquals(next, response.getStatus());
            assertEquals(next == TenantStatus.ACTIVE, tenant.getActive());
            assertFalse(tenant.getUpdatedAt().isBefore(previousUpdate));
        }

        verify(tenantRepository, times(4)).saveAndFlush(tenant);
        verify(auditRepository, times(4)).saveAndFlush(any(PlatformTenantStatusAudit.class));
    }

    @Test
    void auditsTheActorAndTransition() {
        service.updateTenantStatus("acme", TenantStatus.SUSPENDED);

        var captor = org.mockito.ArgumentCaptor.forClass(PlatformTenantStatusAudit.class);
        verify(auditRepository).saveAndFlush(captor.capture());
        PlatformTenantStatusAudit audit = captor.getValue();
        assertEquals(TenantStatus.ACTIVE, audit.getPreviousStatus());
        assertEquals(TenantStatus.SUSPENDED, audit.getNewStatus());
        assertEquals("platform.admin@worknest.test", audit.getActorEmail());
        assertEquals("acme", audit.getTenantKey());
    }

    @Test
    void rejectsProvisioningAsAnAdminManagedStatus() {
        assertThrows(BadRequestException.class,
                () -> service.updateTenantStatus("acme", TenantStatus.PROVISIONING));
        verifyNoInteractions(auditRepository);
    }

    @Test
    void sameStatusIsIdempotentAndDoesNotWriteAnAuditRecord() {
        PlatformTenantResponseDto response = service.updateTenantStatus("acme", TenantStatus.ACTIVE);
        assertEquals(TenantStatus.ACTIVE, response.getStatus());
        verify(tenantRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditRepository);
    }
}
