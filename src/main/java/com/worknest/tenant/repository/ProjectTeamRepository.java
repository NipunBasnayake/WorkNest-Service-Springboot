package com.worknest.tenant.repository;

import com.worknest.tenant.entity.ProjectTeam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, Long> {

    @EntityGraph(attributePaths = {"project", "team"})
    List<ProjectTeam> findByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"project", "team"})
    List<ProjectTeam> findByTeamId(Long teamId);

    @EntityGraph(attributePaths = {"project", "team"})
    List<ProjectTeam> findByTeamIdIn(List<Long> teamIds);

    @EntityGraph(attributePaths = {"project", "team"})
    List<ProjectTeam> findByProjectIdIn(Collection<Long> projectIds);

    Optional<ProjectTeam> findByProjectIdAndTeamId(Long projectId, Long teamId);

    boolean existsByProjectIdAndTeamId(Long projectId, Long teamId);

    boolean existsByTeamId(Long teamId);

    void deleteByProjectId(Long projectId);
}
