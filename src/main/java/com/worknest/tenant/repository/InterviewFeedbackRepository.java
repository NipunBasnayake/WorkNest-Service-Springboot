package com.worknest.tenant.repository;

import com.worknest.tenant.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

    Optional<InterviewFeedback> findByInterviewId(Long interviewId);

    @EntityGraph(attributePaths = "reviewer")
    List<InterviewFeedback> findByInterviewIdIn(Collection<Long> interviewIds);
}
