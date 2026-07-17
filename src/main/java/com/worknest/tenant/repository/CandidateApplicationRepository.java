package com.worknest.tenant.repository;

import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CandidateApplicationRepository extends JpaRepository<CandidateApplication, Long>, JpaSpecificationExecutor<CandidateApplication> {

    @EntityGraph(attributePaths = {"candidate", "jobPosition", "hiredEmployee"})
    Page<CandidateApplication> findAll(Specification<CandidateApplication> specification, Pageable pageable);

    List<CandidateApplication> findByJobPositionIdOrderByAppliedAtDesc(Long jobPositionId);

    List<CandidateApplication> findByStatusOrderByUpdatedAtDesc(CandidatePipelineStatus status);

    Page<CandidateApplication> findByCandidateFullNameContainingIgnoreCaseOrCandidateEmailContainingIgnoreCaseOrJobPositionTitleContainingIgnoreCase(
            String candidateName,
            String candidateEmail,
            String jobPositionTitle,
            Pageable pageable);

    long countByStatus(CandidatePipelineStatus status);

    long countByStatusIn(Collection<CandidatePipelineStatus> statuses);

    long countByJobPositionId(Long jobPositionId);

    boolean existsByCandidateIdAndJobPositionId(Long candidateId, Long jobPositionId);

    Optional<CandidateApplication> findByReferenceNumberIgnoreCase(String referenceNumber);

    Optional<CandidateApplication> findFirstByCandidateIdAndJobPositionIdAndStatusNotIn(
            Long candidateId,
            Long jobPositionId,
            Collection<CandidatePipelineStatus> statuses);

    boolean existsByCandidateIdAndStatusAndIdNot(Long candidateId, CandidatePipelineStatus status, Long id);

    long countByJobPositionIdAndStatus(Long jobPositionId, CandidatePipelineStatus status);

    @Query("SELECT ca.jobPosition.id, COUNT(ca) FROM CandidateApplication ca WHERE ca.jobPosition.id IN :jobIds GROUP BY ca.jobPosition.id")
    List<Object[]> countByJobPositionIds(@Param("jobIds") Collection<Long> jobIds);

    @EntityGraph(attributePaths = {"candidate", "jobPosition"})
    List<CandidateApplication> findTop8ByOrderByAppliedAtDesc();

    @Query("""
            SELECT ca FROM CandidateApplication ca
            JOIN ca.candidate c
            JOIN ca.jobPosition j
            WHERE (:status IS NULL OR ca.status = :status)
              AND (:jobPositionId IS NULL OR j.id = :jobPositionId)
              AND (:search = '' OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<CandidateApplication> searchApplications(
            @Param("search") String search,
            @Param("status") CandidatePipelineStatus status,
            @Param("jobPositionId") Long jobPositionId,
            Pageable pageable);

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
