package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Candidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Page<Candidate> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrCurrentTitleContainingIgnoreCase(
            String fullName,
            String email,
            String phone,
            String currentTitle,
            Pageable pageable);
}