package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Interview;
import com.worknest.tenant.enums.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findByScheduledAtBetweenOrderByScheduledAtAsc(LocalDateTime from, LocalDateTime to);

    List<Interview> findByApplicationCandidateIdOrderByScheduledAtDesc(Long candidateId);

    List<Interview> findByApplicationIdOrderByScheduledAtDesc(Long applicationId);

    List<Interview> findTop8ByStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
            List<InterviewStatus> statuses, LocalDateTime scheduledAt);

    boolean existsByApplicationId(Long applicationId);

    long countByScheduledAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusInAndScheduledAtAfter(List<InterviewStatus> statuses, LocalDateTime scheduledAt);
}
