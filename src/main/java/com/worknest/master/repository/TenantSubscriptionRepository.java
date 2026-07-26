package com.worknest.master.repository;

import com.worknest.master.entity.TenantSubscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {

    @EntityGraph(attributePaths = {"tenant", "subscriptionPlan"})
    Optional<TenantSubscription> findByTenantId(Long tenantId);

    @EntityGraph(attributePaths = {"tenant", "subscriptionPlan"})
    Optional<TenantSubscription> findByTenantTenantKeyIgnoreCase(String tenantKey);

    boolean existsByTenantId(Long tenantId);

    @EntityGraph(attributePaths = {"tenant", "subscriptionPlan"})
    List<TenantSubscription> findAllByOrderByTenantCompanyNameAsc();

    long countByExpiresAtBetween(LocalDateTime from, LocalDateTime to);
}
