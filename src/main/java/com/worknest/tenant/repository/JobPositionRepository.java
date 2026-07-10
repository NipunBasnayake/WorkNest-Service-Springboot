package com.worknest.tenant.repository;

import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.enums.JobPositionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {

    Page<JobPosition> findByTitleContainingIgnoreCaseOrDepartmentContainingIgnoreCase(
            String title,
            String department,
            Pageable pageable);

    long countByStatus(JobPositionStatus status);

    default List<JobPosition> findPublishedJobs() {
        return findPublishedJobs(JobPositionStatus.OPEN, LocalDateTime.now());
    }

    @Query("""
            SELECT j
            FROM JobPosition j
            WHERE j.published = true
              AND j.status = :status
              AND COALESCE(j.deleted, false) = false
              AND COALESCE(j.visibleToExternalApplicants, true) = true
              AND j.slug IS NOT NULL
              AND (j.expiresAt IS NULL OR j.expiresAt > :now)
            ORDER BY j.createdAt DESC
            """)
    List<JobPosition> findPublishedJobs(
            @Param("status") JobPositionStatus status,
            @Param("now") LocalDateTime now);

    default Optional<JobPosition> findPublishedJobBySlug(String slug) {
        return findPublishedJobBySlug(slug, JobPositionStatus.OPEN, LocalDateTime.now());
    }

    @Query("""
            SELECT j
            FROM JobPosition j
            WHERE j.slug = :slug
              AND j.published = true
              AND j.status = :status
              AND COALESCE(j.deleted, false) = false
              AND COALESCE(j.visibleToExternalApplicants, true) = true
              AND (j.expiresAt IS NULL OR j.expiresAt > :now)
            """)
    Optional<JobPosition> findPublishedJobBySlug(
            @Param("slug") String slug,
            @Param("status") JobPositionStatus status,
            @Param("now") LocalDateTime now);

    boolean existsBySlug(String slug);
}
