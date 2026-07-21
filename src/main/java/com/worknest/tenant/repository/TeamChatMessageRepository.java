package com.worknest.tenant.repository;

import com.worknest.tenant.entity.TeamChatMessage;
import com.worknest.tenant.enums.ChatType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamChatMessageRepository extends JpaRepository<TeamChatMessage, Long> {

    @EntityGraph(attributePaths = {"teamChat", "sender"})
    List<TeamChatMessage> findByTeamChatIdOrderByCreatedAtAsc(Long teamChatId);

    List<TeamChatMessage> findByTeamChatIdInOrderByCreatedAtDesc(Collection<Long> teamChatIds);

    Optional<TeamChatMessage> findFirstByTeamChatIdOrderByCreatedAtDesc(Long teamChatId);

    @Query("""
            SELECT COUNT(message)
            FROM TeamChatMessage message
            WHERE message.teamChat.id = :teamChatId
              AND message.sender.id <> :employeeId
              AND NOT EXISTS (
                    SELECT receipt.id
                    FROM ChatReadReceipt receipt
                    WHERE receipt.chatType = :chatType
                      AND receipt.messageId = message.id
                      AND receipt.employee.id = :employeeId
              )
            """)
    long countUnreadForEmployee(
            @Param("teamChatId") Long teamChatId,
            @Param("employeeId") Long employeeId,
            @Param("chatType") ChatType chatType);

    @Query("""
            SELECT message.teamChat.id, COUNT(message)
            FROM TeamChatMessage message
            WHERE message.teamChat.id IN :teamChatIds
              AND message.sender.id <> :employeeId
              AND NOT EXISTS (
                    SELECT receipt.id
                    FROM ChatReadReceipt receipt
                    WHERE receipt.chatType = :chatType
                      AND receipt.messageId = message.id
                      AND receipt.employee.id = :employeeId
              )
            GROUP BY message.teamChat.id
            """)
    List<Object[]> countUnreadByTeamChatIdsForEmployee(
            @Param("teamChatIds") Collection<Long> teamChatIds,
            @Param("employeeId") Long employeeId,
            @Param("chatType") ChatType chatType);

    void deleteByTeamChatId(Long teamChatId);
}
