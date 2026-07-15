package com.worknest.publicapi.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.storage.FileStorageService;
import com.worknest.common.util.SlugUtils;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.publicapi.dto.PublicApplicationRequestDto;
import com.worknest.publicapi.dto.PublicApplicationResponseDto;
import com.worknest.publicapi.dto.PublicApplicationStatusDto;
import com.worknest.publicapi.dto.PublicCompanyDto;
import com.worknest.publicapi.service.PublicCandidateApplicationService;
import com.worknest.tenant.entity.Candidate;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.entity.RecruitmentApplicationEvent;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.enums.RecruitmentEmailTemplateType;
import com.worknest.tenant.repository.CandidateApplicationRepository;
import com.worknest.tenant.repository.CandidateRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.JobPositionRepository;
import com.worknest.tenant.repository.RecruitmentApplicationEventRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import com.worknest.tenant.service.RecruitmentEmailTemplateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(transactionManager = "transactionManager")
public class PublicCandidateApplicationServiceImpl implements PublicCandidateApplicationService {

    private static final String PUBLIC_SOURCE = "Public Careers";
    private static final List<CandidatePipelineStatus> REAPPLY_ALLOWED_STATUSES = List.of(
            CandidatePipelineStatus.REJECTED,
            CandidatePipelineStatus.WITHDRAWN
    );
    private static final List<PlatformRole> RECIPIENT_ROLES = List.of(
            PlatformRole.TENANT_ADMIN,
            PlatformRole.ADMIN,
            PlatformRole.HR
    );

    private final CandidateRepository candidateRepository;
    private final CandidateApplicationRepository candidateApplicationRepository;
    private final JobPositionRepository jobPositionRepository;
    private final EmployeeRepository employeeRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final MasterTenantLookupService masterTenantLookupService;
    private final RecruitmentEmailTemplateService recruitmentEmailTemplateService;
    private final RecruitmentApplicationEventRepository applicationEventRepository;
    @Value("${app.public-web-base-url:http://localhost:5173}")
    private String publicWebBaseUrl;

    public PublicCandidateApplicationServiceImpl(
            CandidateRepository candidateRepository,
            CandidateApplicationRepository candidateApplicationRepository,
            JobPositionRepository jobPositionRepository,
            EmployeeRepository employeeRepository,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            MasterTenantLookupService masterTenantLookupService,
            RecruitmentEmailTemplateService recruitmentEmailTemplateService,
            RecruitmentApplicationEventRepository applicationEventRepository) {
        this.candidateRepository = candidateRepository;
        this.candidateApplicationRepository = candidateApplicationRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.employeeRepository = employeeRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.masterTenantLookupService = masterTenantLookupService;
        this.recruitmentEmailTemplateService = recruitmentEmailTemplateService;
        this.applicationEventRepository = applicationEventRepository;
    }

    @Override
    public PublicApplicationResponseDto apply(String tenantSlug, String jobSlug, PublicApplicationRequestDto requestDto) {
        PublicCompanyDto company = toCompanyDto(resolveTenant(tenantSlug));
        JobPosition jobPosition = resolvePublicJob(jobSlug);
        String email = normalizeEmail(requestDto.getEmail());

        Candidate candidate = candidateRepository.findByEmailIgnoreCase(email).orElseGet(Candidate::new);
        if (candidate.getId() != null) {
            candidateApplicationRepository.findFirstByCandidateIdAndJobPositionIdAndStatusNotIn(
                    candidate.getId(),
                    jobPosition.getId(),
                    REAPPLY_ALLOWED_STATUSES
            ).ifPresent(existing -> {
                throw new BadRequestException("You already have an active application for this vacancy");
            });
        }

        FileStorageService.StoredFileResult storedResume = storeResume(requestDto.getResume());
        applyCandidateFields(candidate, requestDto, email, storedResume);
        Candidate savedCandidate = candidateRepository.save(candidate);

        CandidateApplication application = new CandidateApplication();
        application.setCandidate(savedCandidate);
        application.setJobPosition(jobPosition);
        application.setStatus(CandidatePipelineStatus.APPLIED);
        application.setCoverLetter(trimToNull(requestDto.getCoverLetter()));
        application.setExpectedSalary(requestDto.getExpectedSalary());
        application.setAvailableFrom(requestDto.getAvailableFrom());
        application.setSource(PUBLIC_SOURCE);

        CandidateApplication savedApplication = candidateApplicationRepository.saveAndFlush(application);
        savedApplication.setReferenceNumber(generateReferenceNumber());
        CandidateApplication finalizedApplication = candidateApplicationRepository.save(savedApplication);

        recordApplicationReceived(finalizedApplication);
        notifyRecruitmentOwners(finalizedApplication);
        auditCandidateApplied(finalizedApplication);
        recruitmentEmailTemplateService.send(
                finalizedApplication,
                RecruitmentEmailTemplateType.APPLICATION_RECEIVED,
                company.getCompanyName(),
                buildCareersLink(company.getTenantSlug()),
                null);

        return PublicApplicationResponseDto.builder()
                .referenceNumber(finalizedApplication.getReferenceNumber())
                .vacancyTitle(jobPosition.getTitle())
                .jobSlug(jobPosition.getSlug())
                .company(company)
                .submittedDate(finalizedApplication.getAppliedAt())
                .message("Your application has been received. A confirmation email is on its way.")
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PublicApplicationStatusDto getStatus(String tenantSlug, String referenceNumber) {
        PublicCompanyDto company = toCompanyDto(resolveTenant(tenantSlug));
        String normalizedReference = normalizeReferenceNumber(referenceNumber);
        CandidateApplication application = candidateApplicationRepository.findByReferenceNumberIgnoreCase(normalizedReference)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        return PublicApplicationStatusDto.builder()
                .referenceNumber(application.getReferenceNumber())
                .vacancyTitle(application.getJobPosition().getTitle())
                .jobSlug(application.getJobPosition().getSlug())
                .status(application.getStatus().name())
                .company(company)
                .submittedDate(application.getAppliedAt())
                .build();
    }

    private PlatformTenant resolveTenant(String tenantSlug) {
        return masterTenantLookupService.findBySlug(tenantSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantSlug));
    }

    private JobPosition resolvePublicJob(String jobSlug) {
        String normalizedSlug = SlugUtils.slugify(jobSlug);
        if (normalizedSlug == null) {
            throw new ResourceNotFoundException("Job vacancy not found");
        }
        return jobPositionRepository.findPublishedJobBySlug(normalizedSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Job vacancy not found"));
    }

    private FileStorageService.StoredFileResult storeResume(MultipartFile resume) {
        if (resume == null || resume.isEmpty()) {
            throw new BadRequestException("Resume is required");
        }
        String originalName = StringUtils.cleanPath(resume.getOriginalFilename() == null ? "resume" : resume.getOriginalFilename());
        String extension = extractExtension(originalName);
        if (!"pdf".equals(extension) && !"docx".equals(extension)) {
            throw new BadRequestException("Only PDF and DOCX resumes are allowed");
        }
        return fileStorageService.store(resume, "doc");
    }

    private void applyCandidateFields(
            Candidate candidate,
            PublicApplicationRequestDto requestDto,
            String email,
            FileStorageService.StoredFileResult storedResume) {
        candidate.setFullName((requestDto.getFirstName().trim() + " " + requestDto.getLastName().trim()).trim());
        candidate.setEmail(email);
        candidate.setPhone(trimToNull(requestDto.getPhone()));
        candidate.setCurrentCity(trimToNull(requestDto.getCurrentCity()));
        candidate.setCountry(trimToNull(requestDto.getCountry()));
        candidate.setLinkedinUrl(trimToNull(requestDto.getLinkedIn()));
        candidate.setPortfolioUrl(trimToNull(requestDto.getPortfolio()));
        candidate.setCurrentCompany(trimToNull(requestDto.getCurrentCompany()));
        candidate.setCurrentTitle(trimToNull(requestDto.getCurrentPosition()));
        candidate.setYearsOfExperience(requestDto.getYearsOfExperience());
        candidate.setSource(PUBLIC_SOURCE);
        candidate.setResumeFileName(storedResume.name());
        candidate.setResumeFileUrl(storedResume.path());
        candidate.setResumeMimeType(storedResume.mimeType());
        candidate.setResumeFileSizeBytes(storedResume.size());
    }

    private void notifyRecruitmentOwners(CandidateApplication application) {
        List<Employee> recipients = employeeRepository.findByRoleInAndStatus(RECIPIENT_ROLES, UserStatus.ACTIVE)
                .stream()
                .filter(employee -> employee.getId() != null)
                .distinct()
                .toList();
        String message = "New application " + application.getReferenceNumber()
                + " received for " + application.getJobPosition().getTitle()
                + " from " + application.getCandidate().getEmail() + ".";

        for (Employee recipient : recipients) {
            notificationService.createSystemNotification(
                    recipient.getId(),
                    NotificationType.RECRUITMENT_APPLICATION,
                    message,
                    AuditEntityType.CANDIDATE_APPLICATION.name(),
                    application.getId()
            );
        }
    }

    private void auditCandidateApplied(CandidateApplication application) {
        auditLogService.logAction(
                AuditActionType.CANDIDATE_APPLIED,
                AuditEntityType.CANDIDATE_APPLICATION,
                application.getId(),
                "{\"referenceNumber\":\"" + escapeJson(application.getReferenceNumber())
                        + "\",\"vacancy\":\"" + escapeJson(application.getJobPosition().getTitle())
                        + "\",\"candidateEmail\":\"" + escapeJson(application.getCandidate().getEmail()) + "\"}"
        );
    }

    private PublicCompanyDto toCompanyDto(PlatformTenant tenant) {
        return PublicCompanyDto.builder()
                .tenantSlug(tenant.getSlug())
                .companyName(tenant.getCompanyName())
                .logoUrl(null)
                .about("Explore current opportunities at " + tenant.getCompanyName() + ".")
                .build();
    }

    private String generateReferenceNumber() {
        return "APP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private void recordApplicationReceived(CandidateApplication application) {
        RecruitmentApplicationEvent event = new RecruitmentApplicationEvent();
        event.setApplication(application);
        event.setEventType("APPLIED");
        event.setTitle("Application received");
        event.setDetail("Submitted through the public careers page");
        applicationEventRepository.save(event);
    }

    private String buildCareersLink(String tenantSlug) {
        String base = publicWebBaseUrl == null ? "" : publicWebBaseUrl.replaceAll("/+$", "");
        return base + "/" + tenantSlug + "/careers";
    }

    private String normalizeReferenceNumber(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ResourceNotFoundException("Application not found");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        String normalized = trimToNull(email);
        if (normalized == null) {
            throw new BadRequestException("Email is required");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String extractExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw new BadRequestException("Resume file extension is required");
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
