package com.worknest.tenant.repository;

import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CandidateApplicationRepository extends JpaRepository<CandidateApplication, Long> {

    List<CandidateApplication> findByJobPositionIdOrderByAppliedAtDesc(Long jobPositionId);

    List<CandidateApplication> findByStatusOrderByUpdatedAtDesc(CandidatePipelineStatus status);

    Page<CandidateApplication> findByCandidateFullNameContainingIgnoreCaseOrCandidateEmailContainingIgnoreCaseOrJobPositionTitleContainingIgnoreCase(
            String candidateName,
            String candidateEmail,
            String jobPositionTitle,
            Pageable pageable);

    long countByStatus(CandidatePipelineStatus status);

    long countByJobPositionId(Long jobPositionId);

    boolean existsByCandidateIdAndJobPositionId(Long candidateId, Long jobPositionId);

    Optional<CandidateApplication> findByReferenceNumberIgnoreCase(String referenceNumber);

    Optional<CandidateApplication> findFirstByCandidateIdAndJobPositionIdAndStatusNotIn(
            Long candidateId,
            Long jobPositionId,
            Collection<CandidatePipelineStatus> statuses);

    boolean existsByCandidateIdAndStatusAndIdNot(Long candidateId, CandidatePipelineStatus status, Long id);

    @Query("""
            SELECT ca.status, COUNT(ca) FROM CandidateApplication ca
            WHERE ca.appliedAt BETWEEN :fromDate AND :toDate AND (:status IS NULL OR ca.status = :status)
            GROUP BY ca.status
            """)
    List<Object[]> countPipelineForReport(@Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate, @Param("status") CandidatePipelineStatus status);

    @Query("""
            SELECT ca.jobPosition.title, COUNT(ca) FROM CandidateApplication ca
            WHERE ca.appliedAt BETWEEN :fromDate AND :toDate AND (:status IS NULL OR ca.status = :status)
            GROUP BY ca.jobPosition.id, ca.jobPosition.title ORDER BY COUNT(ca) DESC
            """)
    List<Object[]> countApplicationsByJobForReport(@Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate, @Param("status") CandidatePipelineStatus status);

    @Query(value = """
            SELECT DATE_FORMAT(ca.updated_at, '%Y-%m'), SUM(CASE WHEN ca.status = 'HIRED' THEN 1 ELSE 0 END)
            FROM candidate_applications ca WHERE ca.updated_at BETWEEN :fromDate AND :toDate
            GROUP BY DATE_FORMAT(ca.updated_at, '%Y-%m') ORDER BY DATE_FORMAT(ca.updated_at, '%Y-%m')
            """, nativeQuery = true)
    List<Object[]> countHiringTrend(@Param("fromDate") LocalDateTime fromDate, @Param("toDate") LocalDateTime toDate);
}
