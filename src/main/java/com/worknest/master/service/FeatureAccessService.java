package com.worknest.master.service;

import com.worknest.master.enums.FeatureKey;

public interface FeatureAccessService {
    boolean hasFeature(Long tenantId, FeatureKey featureKey);

    boolean hasFeature(String tenantKey, FeatureKey featureKey);

    void requireFeature(Long tenantId, FeatureKey featureKey);

    void requireFeature(String tenantKey, FeatureKey featureKey);
}
