package com.worknest.master.repository;

import com.worknest.master.entity.TenantBranding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantBrandingRepository extends JpaRepository<TenantBranding, Long> {
    Optional<TenantBranding> findByTenantId(Long tenantId);
}
