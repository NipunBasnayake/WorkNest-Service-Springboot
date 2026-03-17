package com.worknest.tenant.repository;

import com.worknest.tenant.entity.TeamChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamChatMessageRepository extends JpaRepository<TeamChatMessage, Long> {

    List<TeamChatMessage> findByTeamChatIdOrderByCreatedAtAsc(Long teamChatId);

    void deleteByTeamChatId(Long teamChatId);
}
