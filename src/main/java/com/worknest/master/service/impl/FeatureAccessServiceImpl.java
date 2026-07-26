package com.worknest.master.service.impl;

import com.worknest.common.exception.SubscriptionFeatureAccessException;
import com.worknest.master.enums.FeatureKey;
import com.worknest.master.repository.PlanFeatureRepository;
import com.worknest.master.service.FeatureAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional(transactionManager = "masterTransactionManager", readOnly = true)
public class FeatureAccessServiceImpl implements FeatureAccessService {

    private final PlanFeatureRepository planFeatureRepository;
    private final Clock clock;

    public FeatureAccessServiceImpl(PlanFeatureRepository planFeatureRepository, Clock clock) {
        this.planFeatureRepository = planFeatureRepository;
        this.clock = clock;
    }

    @Override
    public boolean hasFeature(Long tenantId, FeatureKey featureKey) {
        if (tenantId == null || featureKey == null) {
            return false;
        }
        return planFeatureRepository.tenantHasFeature(
                tenantId,
                featureKey,
                LocalDateTime.now(clock));
    }

    @Override
    public boolean hasFeature(String tenantKey, FeatureKey featureKey) {
        if (tenantKey == null || tenantKey.isBlank() || featureKey == null) {
            return false;
        }
        return planFeatureRepository.tenantHasFeature(
                tenantKey.trim(),
                featureKey,
                LocalDateTime.now(clock));
    }

    @Override
    public void requireFeature(Long tenantId, FeatureKey featureKey) {
        if (!hasFeature(tenantId, featureKey)) {
            throw new SubscriptionFeatureAccessException();
        }
    }

    @Override
    public void requireFeature(String tenantKey, FeatureKey featureKey) {
        if (!hasFeature(tenantKey, featureKey)) {
            throw new SubscriptionFeatureAccessException();
        }
    }
}
