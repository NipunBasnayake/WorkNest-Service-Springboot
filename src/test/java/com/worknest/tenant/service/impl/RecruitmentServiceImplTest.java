package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.storage.FileStorageService;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.tenant.dto.employee.EmployeeCreateRequestDto;
import com.worknest.tenant.dto.employee.EmployeeResponseDto;
import com.worknest.tenant.dto.recruitment.CandidateApplicationUpdateRequestDto;
import com.worknest.tenant.dto.recruitment.RecruitmentHireRequestDto;
import com.worknest.tenant.entity.Candidate;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import com.worknest.tenant.repository.*;
import com.worknest.tenant.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        verify(employeeService, never()).createEmployeeFromRecruitment(any());
    }

    @Test
    void hireCandidateWithoutSkillsCreatesEmployeeAndMarksApplicationHired() {
        CandidateApplication application = application(CandidatePipelineStatus.OFFERED);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(interviewRepository.existsByApplicationId(1L)).thenReturn(true);
        when(applicationRepository.existsByCandidateIdAndStatusAndIdNot(
                2L, CandidatePipelineStatus.HIRED, 1L)).thenReturn(false);
        when(employeeRepository.existsByEmailIgnoreCase("alex@example.com")).thenReturn(false);

        EmployeeResponseDto createdResponse = EmployeeResponseDto.builder()
                .id(7L)
                .employeeCode("WN-HIRE-007")
                .firstName("Alex")
                .lastName("Perera")
                .email("alex@example.com")
                .role(PlatformRole.EMPLOYEE)
                .status(UserStatus.ACTIVE)
                .joinedDate(LocalDate.now())
                .build();
        when(employeeService.createEmployeeFromRecruitment(any(EmployeeCreateRequestDto.class))).thenReturn(createdResponse);

        Employee createdEmployee = new Employee();
        createdEmployee.setId(7L);
        createdEmployee.setEmployeeCode("WN-HIRE-007");
        createdEmployee.setFirstName("Alex");
        createdEmployee.setLastName("Perera");
        createdEmployee.setEmail("alex@example.com");
        createdEmployee.setRole(PlatformRole.EMPLOYEE);
        createdEmployee.setStatus(UserStatus.ACTIVE);
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(createdEmployee));
        when(applicationRepository.save(any(CandidateApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationService.getCurrentTenantKeyOrThrow()).thenReturn("tenant-key");
        when(masterTenantLookupService.findByTenantKey("tenant-key")).thenReturn(Optional.empty());

        RecruitmentHireRequestDto request = new RecruitmentHireRequestDto();
        request.setTemporaryPassword("WorkNest@2026");

        var response = service.hireApplication(1L, request);

        ArgumentCaptor<EmployeeCreateRequestDto> employeeRequest =
                ArgumentCaptor.forClass(EmployeeCreateRequestDto.class);
        verify(employeeService).createEmployeeFromRecruitment(employeeRequest.capture());
        assertThat(employeeRequest.getValue().getSkills()).isEmpty();
        assertThat(response.getEmployee().getId()).isEqualTo(7L);
        assertThat(response.getApplication().getStatus()).isEqualTo(CandidatePipelineStatus.HIRED);
        assertThat(application.getHiredEmployee()).isSameAs(createdEmployee);
        assertThat(application.getHiredAt()).isNotNull();
    }

    @Test
    void hiringCandidateTwiceIsRejectedBeforeEmployeeCreation() {
        CandidateApplication application = application(CandidatePipelineStatus.HIRED);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.hireApplication(1L, new RecruitmentHireRequestDto()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Candidate application is already hired");
        verify(employeeService, never()).createEmployeeFromRecruitment(any());
    }

    @Test
    void hiringApplicationWithInvalidCandidateIsRejected() {
        CandidateApplication application = application(CandidatePipelineStatus.OFFERED);
        application.setCandidate(null);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.hireApplication(1L, new RecruitmentHireRequestDto()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Candidate not found for application: 1");
        verify(employeeService, never()).createEmployeeFromRecruitment(any());
    }

    @Test
    void hiringInvalidApplicationIsRejected() {
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.hireApplication(999L, new RecruitmentHireRequestDto()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Application not found");
        verify(employeeService, never()).createEmployeeFromRecruitment(any());
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
