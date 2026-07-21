package com.worknest.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.master.entity.PlatformUser;
import com.worknest.security.jwt.JwtService;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.service.TenantSecurityValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterPasswordChangeTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private TenantSecurityValidator tenantSecurityValidator;

    private JwtAuthenticationFilter filter;
    private PlatformUserPrincipal principal;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                jwtService,
                userDetailsService,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                "X-Tenant-Slug",
                tenantSecurityValidator);
        PlatformUser user = new PlatformUser();
        user.setId(1L);
        user.setEmail("platform.admin@worknest.local");
        user.setFullName("Platform Admin");
        user.setPasswordHash("encoded-password");
        user.setRole(PlatformRole.PLATFORM_ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangeRequired(true);
        principal = new PlatformUserPrincipal(user);

        when(jwtService.extractUsername("first-login-token")).thenReturn(principal.getUsername());
        when(userDetailsService.loadUserByUsername(principal.getUsername())).thenReturn(principal);
        when(jwtService.isTokenValid("first-login-token", principal)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passwordRequiredTokenCannotAccessPlatformApis() throws Exception {
        MockHttpServletRequest request = authorizedRequest("GET", "/api/platform/tenants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void passwordRequiredTokenCanCallRequiredPasswordChangeEndpoint() throws Exception {
        MockHttpServletRequest request = authorizedRequest("POST", "/api/auth/change-password-required");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    private MockHttpServletRequest authorizedRequest(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "Bearer first-login-token");
        return request;
    }
}
