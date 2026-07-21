package com.worknest.tenant.repository;

import com.worknest.tenant.entity.HrMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HrMessageRepository extends JpaRepository<HrMessage, Long> {

    @EntityGraph(attributePaths = {"conversation", "sender"})
    List<HrMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<HrMessage> findByConversationIdInOrderByCreatedAtDesc(Collection<Long> conversationIds);

    Optional<HrMessage> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    long countByConversationIdAndSenderIdNotAndReadFalse(Long conversationId, Long senderId);

    long countByConversationIdAndSenderIdAndReadFalse(Long conversationId, Long senderId);

    List<HrMessage> findByConversationIdAndSenderIdNotAndReadFalse(Long conversationId, Long senderId);

    List<HrMessage> findByConversationIdAndSenderIdAndReadFalse(Long conversationId, Long senderId);

    @Query("""
            SELECT message.conversation.id, COUNT(message)
            FROM HrMessage message
            WHERE message.conversation.id IN :conversationIds
              AND message.read = false
              AND message.sender.id = message.conversation.employee.id
            GROUP BY message.conversation.id
            """)
    List<Object[]> countUnreadEmployeeMessagesByConversationIds(
            @Param("conversationIds") Collection<Long> conversationIds);

    @Query("""
            SELECT message.conversation.id, COUNT(message)
            FROM HrMessage message
            WHERE message.conversation.id IN :conversationIds
              AND message.read = false
              AND message.sender.id <> :employeeId
            GROUP BY message.conversation.id
            """)
    List<Object[]> countUnreadMessagesByConversationIdsAndSenderIdNot(
            @Param("conversationIds") Collection<Long> conversationIds,
            @Param("employeeId") Long employeeId);
}
