package com.worknest.master.repository;

import com.worknest.master.entity.SubscriptionFeature;
import com.worknest.master.enums.FeatureKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionFeatureRepository extends JpaRepository<SubscriptionFeature, Long> {
    Optional<SubscriptionFeature> findByFeatureKey(FeatureKey featureKey);

    List<SubscriptionFeature> findAllByOrderByFeatureKeyAsc();
}
