package com.worknest.master.repository;

import com.worknest.master.entity.PlatformTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformTenantRepository extends JpaRepository<PlatformTenant, Long> {

    Optional<PlatformTenant> findByTenantKey(String tenantKey);
    boolean existsByTenantKey(String tenantKey);
}

