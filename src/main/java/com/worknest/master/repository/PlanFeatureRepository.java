package com.worknest.master.repository;

import com.worknest.master.entity.PlanFeature;
import com.worknest.master.enums.FeatureKey;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {

    @EntityGraph(attributePaths = {"plan", "feature"})
    List<PlanFeature> findAllByOrderByFeatureFeatureKeyAscPlanDisplayOrderAsc();

    @EntityGraph(attributePaths = {"plan", "feature"})
    List<PlanFeature> findByPlanId(Long planId);

    Optional<PlanFeature> findByPlanIdAndFeatureFeatureKey(Long planId, FeatureKey featureKey);

    boolean existsByPlanIdAndFeatureId(Long planId, Long featureId);

    void deleteByPlanId(Long planId);

    @Query("""
            select case when count(ts) > 0 then true else false end
            from TenantSubscription ts
            join ts.subscriptionPlan plan
            where ts.tenant.id = :tenantId
              and ts.active = true
              and plan.active = true
              and (ts.expiresAt is null or ts.expiresAt > :now)
              and exists (
                  select pf.id
                  from PlanFeature pf
                  join pf.feature feature
                  where pf.plan = plan
                    and feature.featureKey = :featureKey
                    and pf.enabled = true
              )
            """)
    boolean tenantHasFeature(
            @Param("tenantId") Long tenantId,
            @Param("featureKey") FeatureKey featureKey,
            @Param("now") LocalDateTime now);

    @Query("""
            select case when count(ts) > 0 then true else false end
            from TenantSubscription ts
            join ts.tenant tenant
            join ts.subscriptionPlan plan
            where lower(tenant.tenantKey) = lower(:tenantKey)
              and ts.active = true
              and plan.active = true
              and (ts.expiresAt is null or ts.expiresAt > :now)
              and exists (
                  select pf.id
                  from PlanFeature pf
                  join pf.feature feature
                  where pf.plan = plan
                    and feature.featureKey = :featureKey
                    and pf.enabled = true
              )
            """)
    boolean tenantHasFeature(
            @Param("tenantKey") String tenantKey,
            @Param("featureKey") FeatureKey featureKey,
            @Param("now") LocalDateTime now);
}
