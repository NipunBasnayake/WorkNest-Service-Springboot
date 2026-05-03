package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByOrderByCreatedAtDesc();

    List<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Announcement> findAllByOrderByPinnedDescCreatedAtDesc();

    List<Announcement> findByTeamIsNullOrderByPinnedDescCreatedAtDesc();

    @Query("""
            SELECT a
            FROM Announcement a
            WHERE (
                    :search IS NULL OR :search = ''
                    OR LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.content) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Announcement> search(@Param("search") String search, Pageable pageable);

    @Query("""
            SELECT a
            FROM Announcement a
            WHERE (
                   :isPrivileged = true
                   OR (:viewerEmployeeId IS NOT NULL AND a.createdBy.id = :viewerEmployeeId)
                   OR a.team IS NULL
                   OR (:viewerEmployeeId IS NOT NULL AND a.team.manager IS NOT NULL AND a.team.manager.id = :viewerEmployeeId)
                   OR EXISTS (
                        SELECT tm.id
                        FROM TeamMember tm
                        WHERE tm.team.id = a.team.id
                          AND tm.employee.id = :viewerEmployeeId
                          AND tm.leftAt IS NULL
                   )
                  )
            ORDER BY a.pinned DESC, a.createdAt DESC
            """)
    List<Announcement> findVisibleAnnouncements(
            @Param("viewerEmployeeId") Long viewerEmployeeId,
            @Param("isPrivileged") boolean isPrivileged);

    @Query("""
            SELECT a
            FROM Announcement a
            WHERE (
                   (:viewerEmployeeId IS NOT NULL AND a.createdBy.id = :viewerEmployeeId)
                   OR a.team IS NULL
                   OR (:viewerEmployeeId IS NOT NULL AND a.team.manager IS NOT NULL AND a.team.manager.id = :viewerEmployeeId)
                   OR EXISTS (
                        SELECT tm.id
                        FROM TeamMember tm
                        WHERE tm.team.id = a.team.id
                          AND tm.employee.id = :viewerEmployeeId
                          AND tm.leftAt IS NULL
                   )
                  )
            ORDER BY a.pinned DESC, a.createdAt DESC
            """)
    List<Announcement> findVisibleForEmployee(@Param("viewerEmployeeId") Long viewerEmployeeId);

    @Query("""
            SELECT a
            FROM Announcement a
            WHERE (
                   :isPrivileged = true
                   OR (:viewerEmployeeId IS NOT NULL AND a.createdBy.id = :viewerEmployeeId)
                   OR a.team IS NULL
                   OR (:viewerEmployeeId IS NOT NULL AND a.team.manager IS NOT NULL AND a.team.manager.id = :viewerEmployeeId)
                   OR EXISTS (
                        SELECT tm.id
                        FROM TeamMember tm
                        WHERE tm.team.id = a.team.id
                          AND tm.employee.id = :viewerEmployeeId
                          AND tm.leftAt IS NULL
                   )
                  )
              AND (
                    :search IS NULL OR :search = ''
                    OR LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.content) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Announcement> searchVisible(
            @Param("viewerEmployeeId") Long viewerEmployeeId,
            @Param("isPrivileged") boolean isPrivileged,
            @Param("search") String search,
            Pageable pageable);
}
