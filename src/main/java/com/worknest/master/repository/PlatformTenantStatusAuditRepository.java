package com.worknest.master.repository;

import com.worknest.master.entity.PlatformTenantStatusAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformTenantStatusAuditRepository extends JpaRepository<PlatformTenantStatusAudit, Long> {
}
