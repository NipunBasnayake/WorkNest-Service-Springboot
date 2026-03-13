package com.worknest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Allow credentials
        config.setAllowCredentials(true);

        // Allow all origins for Phase 1 testing (restrict in production)
        config.addAllowedOriginPattern("*");

        // Allow common headers including tenant header
        config.setAllowedHeaders(Arrays.asList("*"));

        // Allow all HTTP methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Expose custom headers
        config.setExposedHeaders(Arrays.asList("X-Tenant-ID", "Authorization"));

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}

