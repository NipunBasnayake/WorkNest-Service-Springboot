package com.worknest.master.repository;

import com.worknest.common.enums.PlatformRole;
import com.worknest.master.entity.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {

    Optional<PlatformUser> findByEmailIgnoreCase(String email);

    Optional<PlatformUser> findByEmailIgnoreCaseAndTenantKey(String email, String tenantKey);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndTenantKey(String email, String tenantKey);

    boolean existsByRole(PlatformRole role);
}
