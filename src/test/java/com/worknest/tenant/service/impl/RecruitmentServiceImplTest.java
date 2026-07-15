package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.storage.FileStorageService;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.tenant.dto.recruitment.CandidateApplicationUpdateRequestDto;
import com.worknest.tenant.dto.recruitment.RecruitmentHireRequestDto;
import com.worknest.tenant.entity.Candidate;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import com.worknest.tenant.repository.*;
import com.worknest.tenant.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentServiceImplTest {

    @Mock private JobPositionRepository jobRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private CandidateCommentRepository commentRepository;
    @Mock private CandidateApplicationRepository applicationRepository;
    @Mock private InterviewRepository interviewRepository;
    @Mock private InterviewFeedbackRepository feedbackRepository;
    @Mock private RecruitmentApplicationEventRepository eventRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private AuditLogService auditLogService;
    @Mock private EmployeeService employeeService;
    @Mock private NotificationService notificationService;
    @Mock private FileStorageService fileStorageService;
    @Mock private RecruitmentEmailTemplateService emailTemplateService;
    @Mock private MasterTenantLookupService masterTenantLookupService;

    private RecruitmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RecruitmentServiceImpl(
                jobRepository, candidateRepository, commentRepository, applicationRepository,
                interviewRepository, feedbackRepository, eventRepository, employeeRepository,
                teamRepository, teamMemberRepository, authorizationService, auditLogService,
                employeeService, notificationService, new TenantDtoMapper(), fileStorageService,
                emailTemplateService, masterTenantLookupService);
    }

    @Test
    void hireConversionCannotGrantTenantAdministratorRole() {
        CandidateApplication application = application(CandidatePipelineStatus.OFFERED);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(interviewRepository.existsByApplicationId(1L)).thenReturn(true);
        when(applicationRepository.existsByCandidateIdAndStatusAndIdNot(2L, CandidatePipelineStatus.HIRED, 1L)).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCase("alex@example.com")).thenReturn(false);

        RecruitmentHireRequestDto request = new RecruitmentHireRequestDto();
        request.setRole(PlatformRole.TENANT_ADMIN);
        request.setDesignation("Frontend Developer");
        request.setDepartment("Engineering");

        assertThatThrownBy(() -> service.hireApplication(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only Employee or Manager");
        verify(employeeService, never()).createEmployee(any());
    }

    @Test
    void statusOnlyMovePreservesExistingNotesAndExpectedSalary() {
        CandidateApplication application = application(CandidatePipelineStatus.APPLIED);
        application.setRecruiterNotes("Strong React background");
        application.setExpectedSalary(new BigDecimal("250000.00"));
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(CandidateApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationRepository.countByJobPositionId(3L)).thenReturn(1L);

        CandidateApplicationUpdateRequestDto request = new CandidateApplicationUpdateRequestDto();
        request.setStatus(CandidatePipelineStatus.SHORTLISTED);

        var response = service.updateApplicationStatus(1L, request);

        assertThat(response.getStatus()).isEqualTo(CandidatePipelineStatus.SHORTLISTED);
        assertThat(response.getRecruiterNotes()).isEqualTo("Strong React background");
        assertThat(response.getExpectedSalary()).isEqualByComparingTo("250000.00");
    }

    @Test
    void rejectingAnApplicationRequiresAReason() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application(CandidatePipelineStatus.APPLIED)));
        CandidateApplicationUpdateRequestDto request = new CandidateApplicationUpdateRequestDto();
        request.setStatus(CandidatePipelineStatus.REJECTED);

        assertThatThrownBy(() -> service.updateApplicationStatus(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("rejection reason");
        verify(applicationRepository, never()).save(any());
    }

    private CandidateApplication application(CandidatePipelineStatus status) {
        Candidate candidate = new Candidate();
        candidate.setId(2L);
        candidate.setFullName("Alex Perera");
        candidate.setEmail("alex@example.com");

        JobPosition job = new JobPosition();
        job.setId(3L);
        job.setTitle("Frontend Developer");
        job.setDepartment("Engineering");
        job.setOpenings(1);

        CandidateApplication application = new CandidateApplication();
        application.setId(1L);
        application.setCandidate(candidate);
        application.setJobPosition(job);
        application.setStatus(status);
        return application;
    }
}
