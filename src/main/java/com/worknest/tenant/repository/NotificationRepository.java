package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Notification;
import com.worknest.tenant.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Query("""
            SELECT n
            FROM Notification n
            WHERE n.recipient.id = :recipientId
              AND (:readFilter IS NULL OR n.read = :readFilter)
              AND (:type IS NULL OR n.type = :type)
            """)
    Page<Notification> searchMyNotifications(
            @Param("recipientId") Long recipientId,
            @Param("readFilter") Boolean readFilter,
            @Param("type") NotificationType type,
            Pageable pageable);
}
