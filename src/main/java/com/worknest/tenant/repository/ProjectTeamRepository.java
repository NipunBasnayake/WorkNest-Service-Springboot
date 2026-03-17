package com.worknest.tenant.repository;

import com.worknest.tenant.entity.ProjectTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, Long> {

    List<ProjectTeam> findByProjectId(Long projectId);

    List<ProjectTeam> findByTeamIdIn(List<Long> teamIds);

    Optional<ProjectTeam> findByProjectIdAndTeamId(Long projectId, Long teamId);

    boolean existsByProjectIdAndTeamId(Long projectId, Long teamId);

    boolean existsByTeamId(Long teamId);

    void deleteByProjectId(Long projectId);
}
