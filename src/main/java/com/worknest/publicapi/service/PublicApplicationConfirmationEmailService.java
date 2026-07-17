package com.worknest.publicapi.service;

import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import com.worknest.tenant.repository.CandidateApplicationRepository;
import com.worknest.tenant.service.RecruitmentEmailTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicApplicationConfirmationEmailService {

    private final CandidateApplicationRepository candidateApplicationRepository;
    private final RecruitmentEmailTemplateService recruitmentEmailTemplateService;

    public PublicApplicationConfirmationEmailService(
            CandidateApplicationRepository candidateApplicationRepository,
            RecruitmentEmailTemplateService recruitmentEmailTemplateService) {
        this.candidateApplicationRepository = candidateApplicationRepository;
        this.recruitmentEmailTemplateService = recruitmentEmailTemplateService;
    }

    @Transactional(
            transactionManager = "transactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void queueApplicationReceivedEmail(
            Long applicationId,
            String companyName,
            String careersLink) {
        CandidateApplication application = candidateApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        recruitmentEmailTemplateService.send(
                application,
                RecruitmentEmailTemplateType.APPLICATION_RECEIVED,
                companyName,
                careersLink,
                null);
    }
}
