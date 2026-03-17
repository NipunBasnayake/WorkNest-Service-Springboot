package com.worknest.tenant.repository;

import com.worknest.tenant.entity.HrMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HrMessageRepository extends JpaRepository<HrMessage, Long> {

    List<HrMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<HrMessage> findByConversationIdAndSenderIdNotAndReadFalse(Long conversationId, Long senderId);
}
