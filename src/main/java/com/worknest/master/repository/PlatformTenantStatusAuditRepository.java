package com.worknest.master.repository;

import com.worknest.master.entity.PlatformTenantStatusAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformTenantStatusAuditRepository extends JpaRepository<PlatformTenantStatusAudit, Long> {
    List<PlatformTenantStatusAudit> findTop100ByOrderByChangedAtDesc();
}
