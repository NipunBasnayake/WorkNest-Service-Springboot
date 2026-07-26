package com.worknest.master.repository;

import com.worknest.master.entity.SubscriptionAuditLog;
import com.worknest.master.enums.SubscriptionAuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SubscriptionAuditLogRepository extends JpaRepository<SubscriptionAuditLog, Long> {
    long countByActionAndOccurredAtAfter(SubscriptionAuditAction action, LocalDateTime occurredAfter);
}
