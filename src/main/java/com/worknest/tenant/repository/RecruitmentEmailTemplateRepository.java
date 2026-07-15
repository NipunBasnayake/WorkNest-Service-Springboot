package com.worknest.tenant.repository;

import com.worknest.tenant.entity.RecruitmentEmailTemplate;
import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecruitmentEmailTemplateRepository extends JpaRepository<RecruitmentEmailTemplate, Long> {
    Optional<RecruitmentEmailTemplate> findByType(RecruitmentEmailTemplateType type);
}
