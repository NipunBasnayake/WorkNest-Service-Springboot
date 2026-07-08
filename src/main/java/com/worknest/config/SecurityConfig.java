package com.worknest.config;

import com.worknest.security.filter.JwtAuthenticationFilter;
import com.worknest.security.handler.RestAccessDeniedHandler;
import com.worknest.security.handler.RestAuthenticationEntryPoint;
import com.worknest.tenant.filter.TenantResolutionFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final TenantResolutionFilter tenantResolutionFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final boolean publicHealthEnabled;
    private final boolean swaggerPublicEnabled;

    public SecurityConfig(
            TenantResolutionFilter tenantResolutionFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            @Value("${app.security.public-health-enabled:true}") boolean publicHealthEnabled,
            @Value("${app.security.swagger-public-enabled:false}") boolean swaggerPublicEnabled) {
        this.tenantResolutionFilter = tenantResolutionFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.publicHealthEnabled = publicHealthEnabled;
        this.swaggerPublicEnabled = swaggerPublicEnabled;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * Filters are registered ONLY through the SecurityFilterChain below
     * (.addFilterBefore).  The @Component-annotated filter classes are excluded
     * from the servlet-container auto-registration path by NOT declaring any
     * FilterRegistrationBean.  This avoids the confusing
     * "was not registered (disabled)" log messages and guarantees a single
     * filter chain execution order.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("wn_csrf_token");
        csrfTokenRepository.setHeaderName("X-CSRF-TOKEN");
        csrfTokenRepository.setCookiePath("/");

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(
                                "/api/**",
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/register-company",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/platform/onboarding/tenants",
                                "/ws/**",
                                "/error"
                        )
                )
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> {
                    authorize
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/register-company",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/public/**",
                                "/api/platform/onboarding/tenants",
                                "/error",
                                "/ws/**")
                        .permitAll();

                    if (publicHealthEnabled) {
                        authorize.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    }

                    if (swaggerPublicEnabled) {
                        authorize.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }

                    authorize
                        .requestMatchers("/api/auth/logout", "/api/auth/refresh", "/api/auth/me", "/api/auth/change-password", "/api/auth/admin/**")
                        .authenticated()
                        .requestMatchers("/api/platform/**")
                        .hasRole("PLATFORM_ADMIN")
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/api/*/**")
                        .hasAnyRole("TENANT_ADMIN", "ADMIN", "MANAGER", "HR", "EMPLOYEE")
                        .anyRequest()
                        .authenticated();
                })
                .addFilterBefore(tenantResolutionFilter, CsrfFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
