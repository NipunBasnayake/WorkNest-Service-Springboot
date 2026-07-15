package com.worknest.tenant.repository;

import com.worknest.tenant.entity.RecruitmentApplicationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecruitmentApplicationEventRepository extends JpaRepository<RecruitmentApplicationEvent, Long> {
    List<RecruitmentApplicationEvent> findByApplicationIdOrderByOccurredAtDesc(Long applicationId);
}
