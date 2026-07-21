package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Interview;
import com.worknest.tenant.enums.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.LocalDateTime;
import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long>, JpaSpecificationExecutor<Interview> {

    @EntityGraph(attributePaths = {"application", "application.candidate", "application.jobPosition", "interviewer"})
    Page<Interview> findAll(Specification<Interview> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"application", "application.candidate", "application.jobPosition", "interviewer"})
    List<Interview> findByScheduledAtBetweenOrderByScheduledAtAsc(LocalDateTime from, LocalDateTime to);

    List<Interview> findByApplicationCandidateIdOrderByScheduledAtDesc(Long candidateId);

    @EntityGraph(attributePaths = {"application", "application.candidate", "application.jobPosition", "interviewer"})
    List<Interview> findByApplicationIdOrderByScheduledAtDesc(Long applicationId);

    @EntityGraph(attributePaths = {"application", "application.candidate", "application.jobPosition"})
    List<Interview> findTop8ByStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
            List<InterviewStatus> statuses, LocalDateTime scheduledAt);

    boolean existsByApplicationId(Long applicationId);

    long countByScheduledAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusInAndScheduledAtAfter(List<InterviewStatus> statuses, LocalDateTime scheduledAt);
}
