package com.worknest.master.repository;

import com.worknest.master.entity.PlatformTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformTenantRepository extends JpaRepository<PlatformTenant, Long> {

    Optional<PlatformTenant> findByTenantKey(String tenantKey);
    Optional<PlatformTenant> findBySlug(String slug);
    boolean existsByTenantKey(String tenantKey);
    boolean existsBySlug(String slug);
    boolean existsByCompanyNameIgnoreCase(String companyName);
}

