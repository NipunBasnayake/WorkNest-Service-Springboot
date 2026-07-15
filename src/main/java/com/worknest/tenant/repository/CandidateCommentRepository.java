package com.worknest.tenant.repository;

import com.worknest.tenant.entity.CandidateComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateCommentRepository extends JpaRepository<CandidateComment, Long> {

    List<CandidateComment> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

    List<CandidateComment> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
