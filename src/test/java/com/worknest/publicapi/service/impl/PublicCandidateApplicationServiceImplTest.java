package com.worknest.publicapi.service.impl;

import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.DuplicateApplicationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.storage.FileStorageService;
import com.worknest.common.storage.StorageCategory;
import com.worknest.common.storage.StoredFileDto;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.dto.TenantBrandingViewDto;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.master.service.TenantBrandingService;
import com.worknest.publicapi.dto.PublicApplicationRequestDto;
import com.worknest.publicapi.dto.PublicApplicationResponseDto;
import com.worknest.publicapi.event.PublicApplicationSubmittedEvent;
import com.worknest.tenant.entity.Candidate;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.repository.CandidateApplicationRepository;
import com.worknest.tenant.repository.CandidateRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.JobPositionRepository;
import com.worknest.tenant.repository.RecruitmentApplicationEventRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCandidateApplicationServiceImplTest {

    @Mock private CandidateRepository candidateRepository;
    @Mock private CandidateApplicationRepository candidateApplicationRepository;
    @Mock private JobPositionRepository jobPositionRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private MasterTenantLookupService masterTenantLookupService;
    @Mock private TenantBrandingService tenantBrandingService;
    @Mock private RecruitmentApplicationEventRepository applicationEventRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private PublicCandidateApplicationServiceImpl service;
    private PlatformTenant tenant;
    private JobPosition job;

    @BeforeEach
    void setUp() {
        service = new PublicCandidateApplicationServiceImpl(
                candidateRepository,
                candidateApplicationRepository,
                jobPositionRepository,
                employeeRepository,
                fileStorageService,
                notificationService,
                auditLogService,
                masterTenantLookupService,
                tenantBrandingService,
                applicationEventRepository,
                applicationEventPublisher);

        tenant = new PlatformTenant();
        tenant.setTenantKey("tenant-residue");
        tenant.setSlug("residue-solutions");
        tenant.setCompanyName("Residue Solutions");
        org.mockito.Mockito.lenient().when(tenantBrandingService.getPublicBranding("residue-solutions"))
                .thenReturn(new TenantBrandingViewDto(
                        1L,
                        "tenant-residue",
                        "residue-solutions",
                        "Residue Solutions",
                        "#2563EB",
                        1L,
                        com.worknest.common.enums.TenantStatus.ACTIVE,
                        LocalDateTime.of(2026, 7, 16, 1, 0)
                ));

        job = new JobPosition();
        job.setId(12L);
        job.setSlug("software-engineer");
        job.setTitle("Software Engineer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"resume.pdf", "resume.docx"})
    void appliesWithSupportedResumeAndPublishesEmailAfterCommit(String fileName) {
        PublicApplicationRequestDto request = request(fileName);
        when(masterTenantLookupService.findBySlug("residue-solutions")).thenReturn(Optional.of(tenant));
        when(jobPositionRepository.findPublishedJobBySlug("software-engineer")).thenReturn(Optional.of(job));
        when(candidateRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.empty());
        when(fileStorageService.store(
                "residue-solutions",
                StorageCategory.CANDIDATE_RESUME,
                request.getResume())).thenReturn(storedResume(fileName));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate candidate = invocation.getArgument(0);
            candidate.setId(25L);
            return candidate;
        });
        when(candidateApplicationRepository.saveAndFlush(any(CandidateApplication.class))).thenAnswer(invocation -> {
            CandidateApplication application = invocation.getArgument(0);
            application.setId(40L);
            application.setAppliedAt(LocalDateTime.of(2026, 7, 16, 1, 0));
            return application;
        });
        when(candidateApplicationRepository.save(any(CandidateApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeRepository.findByRoleInAndStatus(any(), any())).thenReturn(List.of());

        PublicApplicationResponseDto response = service.apply(
                "residue-solutions", "software-engineer", request);

        assertThat(response.getReferenceNumber()).startsWith("APP-");
        assertThat(response.getVacancyTitle()).isEqualTo("Software Engineer");
        ArgumentCaptor<PublicApplicationSubmittedEvent> eventCaptor =
                ArgumentCaptor.forClass(PublicApplicationSubmittedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().applicationId()).isEqualTo(40L);
        assertThat(eventCaptor.getValue().tenantKey()).isEqualTo("tenant-residue");
        assertThat(eventCaptor.getValue().tenantSlug()).isEqualTo("residue-solutions");
    }

    @Test
    void duplicateActiveApplicationIsConflictDomainException() {
        PublicApplicationRequestDto request = request("resume.pdf");
        Candidate candidate = new Candidate();
        candidate.setId(25L);
        when(masterTenantLookupService.findBySlug("residue-solutions")).thenReturn(Optional.of(tenant));
        when(jobPositionRepository.findPublishedJobBySlug("software-engineer")).thenReturn(Optional.of(job));
        when(candidateRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(candidate));
        when(candidateApplicationRepository.findFirstByCandidateIdAndJobPositionIdAndStatusNotIn(
                any(), any(), any())).thenReturn(Optional.of(new CandidateApplication()));

        assertThatThrownBy(() -> service.apply("residue-solutions", "software-engineer", request))
                .isInstanceOf(DuplicateApplicationException.class)
                .hasMessage("You already have an active application for this vacancy");
        verify(fileStorageService, never()).store(
                any(String.class),
                eq(StorageCategory.CANDIDATE_RESUME),
                any());
    }

    @Test
    void invalidTenantIsNotFoundBeforeTenantRepositoryAccess() {
        when(masterTenantLookupService.findBySlug("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply("unknown", "software-engineer", request("resume.pdf")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Tenant not found: unknown");
        verify(jobPositionRepository, never()).findPublishedJobBySlug(any());
    }

    @Test
    void invalidJobIsNotFoundBeforeResumeStorage() {
        when(masterTenantLookupService.findBySlug("residue-solutions")).thenReturn(Optional.of(tenant));
        when(jobPositionRepository.findPublishedJobBySlug("missing-job")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply("residue-solutions", "missing-job", request("resume.pdf")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job vacancy not found");
        verify(fileStorageService, never()).store(
                any(String.class),
                eq(StorageCategory.CANDIDATE_RESUME),
                any());
    }

    @Test
    void legacyDottedJobSlugIsResolvedWithoutRewritingBeforeApplicationValidation() {
        PublicApplicationRequestDto request = request("resume.txt");
        job.setSlug("business.analyst-2026-7");
        job.setTitle("Business Analyst");
        when(masterTenantLookupService.findBySlug("residue-solutions")).thenReturn(Optional.of(tenant));
        when(jobPositionRepository.findPublishedJobBySlug("business.analyst-2026-7"))
                .thenReturn(Optional.of(job));
        when(candidateRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(
                "residue-solutions", "Business.Analyst-2026-7", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only PDF and DOCX resumes are allowed");

        verify(jobPositionRepository).findPublishedJobBySlug("business.analyst-2026-7");
        verify(fileStorageService, never()).store(
                any(String.class),
                eq(StorageCategory.CANDIDATE_RESUME),
                any());
    }

    @Test
    void invalidResumeExtensionIsBadRequest() {
        PublicApplicationRequestDto request = request("resume.txt");
        when(masterTenantLookupService.findBySlug("residue-solutions")).thenReturn(Optional.of(tenant));
        when(jobPositionRepository.findPublishedJobBySlug("software-engineer")).thenReturn(Optional.of(job));
        when(candidateRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply("residue-solutions", "software-engineer", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only PDF and DOCX resumes are allowed");
        verify(candidateRepository, never()).save(any());
    }

    private PublicApplicationRequestDto request(String fileName) {
        PublicApplicationRequestDto request = new PublicApplicationRequestDto();
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setEmail("Ada@Example.com");
        request.setResume(new MockMultipartFile(
                "resume",
                fileName,
                "application/octet-stream",
                fileName.endsWith(".pdf") ? "%PDF-test".getBytes() : "PK-test".getBytes()));
        return request;
    }

    private StoredFileDto storedResume(String fileName) {
        return new StoredFileDto(
                "91",
                fileName,
                "recruitment/resumes/managed_" + fileName,
                "/api/residue-solutions/files/91/preview",
                128L,
                fileName.endsWith(".pdf")
                        ? "application/pdf"
                        : "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                Instant.parse("2026-07-16T01:00:00Z"));
    }
}
