package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Project;
import com.worknest.tenant.enums.ProjectStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Override
    @EntityGraph(attributePaths = {"createdBy"})
    List<Project> findAll();

    @Override
    @EntityGraph(attributePaths = {"createdBy"})
    List<Project> findAllById(Iterable<Long> ids);

    List<Project> findByStatus(ProjectStatus status);

    @EntityGraph(attributePaths = {"createdBy"})
    List<Project> findByCreatedById(Long createdById);

    long countByStatus(ProjectStatus status);

    long countByStatusIn(Collection<ProjectStatus> statuses);

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
    @EntityGraph(attributePaths = {"createdBy"})
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
    @EntityGraph(attributePaths = {"createdBy"})
    List<Project> findDistinctByAssignedTeamIds(@Param("teamIds") List<Long> teamIds);

    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM Project p
                    LEFT JOIN ProjectTeam pt ON pt.project.id = p.id
                    LEFT JOIN Team t ON t.id = pt.team.id
                    LEFT JOIN TeamMember tm ON tm.team.id = t.id AND tm.leftAt IS NULL
                    WHERE (
                           p.createdBy.id = :employeeId
                           OR (t.manager IS NOT NULL AND t.manager.id = :employeeId)
                           OR tm.employee.id = :employeeId
                           OR EXISTS (
                                SELECT task.id
                                FROM Task task
                                WHERE task.project.id = p.id
                                  AND (task.assignee.id = :employeeId OR task.createdBy.id = :employeeId)
                           )
                    )
                      AND (:status IS NULL OR p.status = :status)
                      AND (
                            :search IS NULL OR :search = ''
                            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                          )
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.id)
                    FROM Project p
                    LEFT JOIN ProjectTeam pt ON pt.project.id = p.id
                    LEFT JOIN Team t ON t.id = pt.team.id
                    LEFT JOIN TeamMember tm ON tm.team.id = t.id AND tm.leftAt IS NULL
                    WHERE (
                           p.createdBy.id = :employeeId
                           OR (t.manager IS NOT NULL AND t.manager.id = :employeeId)
                           OR tm.employee.id = :employeeId
                           OR EXISTS (
                                SELECT task.id
                                FROM Task task
                                WHERE task.project.id = p.id
                                  AND (task.assignee.id = :employeeId OR task.createdBy.id = :employeeId)
                           )
                    )
                      AND (:status IS NULL OR p.status = :status)
                      AND (
                            :search IS NULL OR :search = ''
                            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                          )
                    """
    )
    @EntityGraph(attributePaths = {"createdBy"})
    Page<Project> searchReadableByEmployee(
            @Param("employeeId") Long employeeId,
            @Param("status") ProjectStatus status,
            @Param("search") String search,
            Pageable pageable);
}
