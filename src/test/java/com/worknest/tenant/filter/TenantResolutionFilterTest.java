package com.worknest.tenant.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worknest.common.enums.TenantStatus;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.multitenancy.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    @Mock private MasterTenantLookupService masterTenantLookupService;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void anonymousPublicCareersRequestResolvesTenantFromUrl() throws Exception {
        PlatformTenant tenant = new PlatformTenant();
        tenant.setSlug("acme");
        tenant.setTenantKey("tenant_acme");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setActive(true);
        when(masterTenantLookupService.findBySlug("acme")).thenReturn(Optional.of(tenant));

        TenantResolutionFilter filter = new TenantResolutionFilter(
                masterTenantLookupService,
                new ObjectMapper(),
                "X-Tenant-ID"
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/public/acme/careers/devops.engineer-2026-4"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> tenantKeyInRequest = new AtomicReference<>();
        AtomicReference<String> tenantSlugInRequest = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            tenantKeyInRequest.set(TenantContextHolder.getTenantKey());
            tenantSlugInRequest.set(TenantContextHolder.getTenantSlug());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantKeyInRequest).hasValue("tenant_acme");
        assertThat(tenantSlugInRequest).hasValue("acme");
        assertThat(TenantContextHolder.getTenantKey()).isNull();
        assertThat(TenantContextHolder.getTenantSlug()).isNull();
    }
}
