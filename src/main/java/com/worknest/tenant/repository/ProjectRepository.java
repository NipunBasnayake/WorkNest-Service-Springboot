package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Project;
import com.worknest.tenant.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByStatus(ProjectStatus status);

    long countByStatus(ProjectStatus status);

    @Query("""
            SELECT p
            FROM Project p
            WHERE (:status IS NULL OR p.status = :status)
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Project> search(
            @Param("status") ProjectStatus status,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT p.status, COUNT(p)
            FROM Project p
            GROUP BY p.status
            """)
    List<Object[]> countByStatusGroup();

    @Query("""
            SELECT DISTINCT p
            FROM Project p
            JOIN ProjectTeam pt ON pt.project.id = p.id
            WHERE pt.team.id IN :teamIds
            """)
    List<Project> findDistinctByAssignedTeamIds(@Param("teamIds") List<Long> teamIds);
}
