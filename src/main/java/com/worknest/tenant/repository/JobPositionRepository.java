package com.worknest.tenant.repository;

import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.enums.JobPositionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPositionRepository extends JpaRepository<JobPosition, Long> {

    Page<JobPosition> findByTitleContainingIgnoreCaseOrDepartmentContainingIgnoreCase(
            String title,
            String department,
            Pageable pageable);

    long countByStatus(JobPositionStatus status);
}