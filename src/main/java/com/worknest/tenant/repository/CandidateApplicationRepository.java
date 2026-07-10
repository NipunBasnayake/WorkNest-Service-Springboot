package com.worknest.tenant.repository;

import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
}
