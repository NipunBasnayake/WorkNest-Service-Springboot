package com.worknest.tenant.repository;

import com.worknest.tenant.entity.HrMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HrMessageRepository extends JpaRepository<HrMessage, Long> {

    List<HrMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    Optional<HrMessage> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    long countByConversationIdAndSenderIdNotAndReadFalse(Long conversationId, Long senderId);

    long countByConversationIdAndSenderIdAndReadFalse(Long conversationId, Long senderId);

    List<HrMessage> findByConversationIdAndSenderIdNotAndReadFalse(Long conversationId, Long senderId);

    List<HrMessage> findByConversationIdAndSenderIdAndReadFalse(Long conversationId, Long senderId);
}
