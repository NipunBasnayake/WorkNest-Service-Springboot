package com.worknest.config;

import com.worknest.security.filter.JwtAuthenticationFilter;
import com.worknest.security.handler.RestAccessDeniedHandler;
import com.worknest.security.handler.RestAuthenticationEntryPoint;
import com.worknest.tenant.filter.TenantResolutionFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock private TenantResolutionFilter tenantResolutionFilter;
    @Mock private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Mock private RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @Mock private RestAccessDeniedHandler restAccessDeniedHandler;

    @Test
    void securityFiltersAreNotAlsoRegisteredAsServletFilters() {
        SecurityConfig config = new SecurityConfig(
                tenantResolutionFilter,
                jwtAuthenticationFilter,
                restAuthenticationEntryPoint,
                restAccessDeniedHandler,
                true,
                false);

        var tenantRegistration = config.tenantResolutionFilterRegistration();
        var jwtRegistration = config.jwtAuthenticationFilterRegistration();

        assertThat(tenantRegistration.isEnabled()).isFalse();
        assertThat(tenantRegistration.getFilter()).isSameAs(tenantResolutionFilter);
        assertThat(jwtRegistration.isEnabled()).isFalse();
        assertThat(jwtRegistration.getFilter()).isSameAs(jwtAuthenticationFilter);
    }
}
