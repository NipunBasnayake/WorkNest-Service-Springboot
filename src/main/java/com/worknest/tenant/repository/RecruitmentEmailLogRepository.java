package com.worknest.tenant.repository;

import com.worknest.tenant.entity.RecruitmentEmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecruitmentEmailLogRepository extends JpaRepository<RecruitmentEmailLog, Long> {
    List<RecruitmentEmailLog> findByApplicationIdOrderBySentAtDesc(Long applicationId);
}
