package com.worknest.master.repository;

import com.worknest.master.entity.TenantOnboardingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantOnboardingRequestRepository extends JpaRepository<TenantOnboardingRequest, Long> {
    Optional<TenantOnboardingRequest> findByIdempotencyKey(String idempotencyKey);
}
