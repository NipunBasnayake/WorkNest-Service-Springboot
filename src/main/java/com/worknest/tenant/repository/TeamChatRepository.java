package com.worknest.tenant.repository;

import com.worknest.tenant.entity.TeamChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamChatRepository extends JpaRepository<TeamChat, Long> {

    Optional<TeamChat> findByTeamId(Long teamId);

    List<TeamChat> findByTeamIdInOrderByUpdatedAtDesc(Collection<Long> teamIds);

    @Query("""
            SELECT tc
            FROM TeamChat tc
            JOIN FETCH tc.team t
            LEFT JOIN FETCH t.manager
            WHERE tc.id = :id
            """)
    Optional<TeamChat> findByIdWithTeamAndManager(@Param("id") Long id);
}
