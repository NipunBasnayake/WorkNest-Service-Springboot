package com.worknest.master.service.impl;

import com.worknest.common.exception.SubscriptionFeatureAccessException;
import com.worknest.master.enums.FeatureKey;
import com.worknest.master.repository.PlanFeatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureAccessServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-26T12:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private PlanFeatureRepository planFeatureRepository;

    private FeatureAccessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FeatureAccessServiceImpl(planFeatureRepository, CLOCK);
    }

    @Test
    void hasFeatureDelegatesToCentralSubscriptionQuery() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        when(planFeatureRepository.tenantHasFeature("acme", FeatureKey.RECRUITMENT, now))
                .thenReturn(true);

        assertThat(service.hasFeature("acme", FeatureKey.RECRUITMENT)).isTrue();
        verify(planFeatureRepository).tenantHasFeature("acme", FeatureKey.RECRUITMENT, now);
    }

    @Test
    void requireFeatureReturnsSubscriptionSpecificDenial() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        when(planFeatureRepository.tenantHasFeature(42L, FeatureKey.PAYROLL, now))
                .thenReturn(false);

        assertThatThrownBy(() -> service.requireFeature(42L, FeatureKey.PAYROLL))
                .isInstanceOf(SubscriptionFeatureAccessException.class)
                .hasMessage("Subscription plan does not include this feature.");
    }
}
