package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Task;
import com.worknest.tenant.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<Task> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);

    List<Task> findByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, TaskStatus status);

    boolean existsByProjectIdAndAssigneeId(Long projectId, Long assigneeId);

    boolean existsByProjectIdAndCreatedById(Long projectId, Long createdById);

    long countByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    long countByStatus(TaskStatus status);

    long countByDueDateBeforeAndStatusNot(LocalDate dueDate, TaskStatus status);

    long countByAssigneeId(Long assigneeId);

    long countByAssigneeIdAndDueDateBeforeAndStatusNot(Long assigneeId, LocalDate dueDate, TaskStatus status);

    long countByAssigneeIdAndProjectIdInAndDueDateBeforeAndStatusNot(
            Long assigneeId,
            List<Long> projectIds,
            LocalDate dueDate,
            TaskStatus status);

    long countByProjectIdIn(List<Long> projectIds);

    long countByProjectIdInAndDueDateBeforeAndStatusNot(List<Long> projectIds, LocalDate dueDate, TaskStatus status);

    @Query("""
            SELECT t
            FROM Task t
            WHERE (:projectId IS NULL OR t.project.id = :projectId)
              AND (:status IS NULL OR t.status = :status)
              AND (:assigneeId IS NULL OR t.assignee.id = :assigneeId)
              AND (:dueFrom IS NULL OR t.dueDate >= :dueFrom)
              AND (:dueTo IS NULL OR t.dueDate <= :dueTo)
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(t.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Task> search(
            @Param("projectId") Long projectId,
            @Param("status") TaskStatus status,
            @Param("assigneeId") Long assigneeId,
            @Param("dueFrom") LocalDate dueFrom,
            @Param("dueTo") LocalDate dueTo,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT t.status, COUNT(t)
            FROM Task t
            GROUP BY t.status
            """)
    List<Object[]> countByStatusGroup();

    @Query("""
            SELECT t.status, COUNT(t)
            FROM Task t
            WHERE t.project.id IN :projectIds
            GROUP BY t.status
            """)
    List<Object[]> countByStatusGroupForProjects(@Param("projectIds") List<Long> projectIds);

    @Query("""
            SELECT t.assignee.id, COUNT(t)
            FROM Task t
            WHERE t.assignee IS NOT NULL
            GROUP BY t.assignee.id
            ORDER BY COUNT(t) DESC
            """)
    List<Object[]> countByAssignee();

    @Query("""
            SELECT t.assignee.id, COUNT(t)
            FROM Task t
            WHERE t.assignee IS NOT NULL
              AND t.project.id IN :projectIds
            GROUP BY t.assignee.id
            ORDER BY COUNT(t) DESC
            """)
    List<Object[]> countByAssigneeForProjects(@Param("projectIds") List<Long> projectIds);

    @Query("""
            SELECT t.project.id, t.project.name, COUNT(t)
            FROM Task t
            GROUP BY t.project.id, t.project.name
            ORDER BY COUNT(t) DESC
            """)
    List<Object[]> countByProject();

    @Query("""
            SELECT t.project.id, t.project.name, COUNT(t)
            FROM Task t
            WHERE t.project.id IN :projectIds
            GROUP BY t.project.id, t.project.name
            ORDER BY COUNT(t) DESC
            """)
    List<Object[]> countByProjectForProjects(@Param("projectIds") List<Long> projectIds);

    @Query("""
            SELECT t.status, COUNT(t)
            FROM Task t
            WHERE t.assignee.id = :assigneeId
            GROUP BY t.status
            """)
    List<Object[]> countMyTasksByStatus(@Param("assigneeId") Long assigneeId);
}
