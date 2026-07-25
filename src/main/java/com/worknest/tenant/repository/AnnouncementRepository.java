package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @EntityGraph(attributePaths = {"createdBy", "team", "team.manager"})
    @Query("SELECT a FROM Announcement a WHERE a.id = :id")
    Optional<Announcement> findWithDetailsById(@Param("id") Long id);

    @Query(value = """
            SELECT DATE_FORMAT(a.created_at, '%Y-%m'), COUNT(*) FROM announcements a
            WHERE a.created_at BETWEEN :fromDate AND :toDate
            GROUP BY DATE_FORMAT(a.created_at, '%Y-%m') ORDER BY DATE_FORMAT(a.created_at, '%Y-%m')
            """, nativeQuery = true)
    List<Object[]> countCreatedForReport(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate);

    @EntityGraph(attributePaths = {"createdBy", "team"})
    List<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT a
            FROM Announcement a
            LEFT JOIN a.team targetTeam
            LEFT JOIN targetTeam.manager teamManager
            WHERE (
                   :isPrivileged = true
                   OR (:viewerEmployeeId IS NOT NULL AND a.createdBy.id = :viewerEmployeeId)
                   OR targetTeam.id IS NULL
                   OR (:viewerEmployeeId IS NOT NULL
                       AND teamManager.id = :viewerEmployeeId)
                   OR EXISTS (
                        SELECT tm.id
                        FROM TeamMember tm
                        WHERE tm.team.id = targetTeam.id
                          AND tm.employee.id = :viewerEmployeeId
                          AND tm.leftAt IS NULL
                   )
                  )
            ORDER BY a.pinned DESC, a.createdAt DESC
            """)
    @EntityGraph(attributePaths = {"createdBy", "team"})
    List<Announcement> findVisibleAnnouncements(
            @Param("viewerEmployeeId") Long viewerEmployeeId,
            @Param("isPrivileged") boolean isPrivileged);

    @Query("""
            SELECT a
            FROM Announcement a
            LEFT JOIN a.team targetTeam
            LEFT JOIN targetTeam.manager teamManager
            WHERE (
                   :isPrivileged = true
                   OR (:viewerEmployeeId IS NOT NULL AND a.createdBy.id = :viewerEmployeeId)
                   OR targetTeam.id IS NULL
                   OR (:viewerEmployeeId IS NOT NULL
                       AND teamManager.id = :viewerEmployeeId)
                   OR EXISTS (
                        SELECT tm.id
                        FROM TeamMember tm
                        WHERE tm.team.id = targetTeam.id
                          AND tm.employee.id = :viewerEmployeeId
                          AND tm.leftAt IS NULL
                   )
                  )
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.content) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.createdByName) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    @EntityGraph(attributePaths = {"createdBy", "team"})
    Page<Announcement> searchVisible(
            @Param("viewerEmployeeId") Long viewerEmployeeId,
            @Param("isPrivileged") boolean isPrivileged,
            @Param("search") String search,
            Pageable pageable);
}
