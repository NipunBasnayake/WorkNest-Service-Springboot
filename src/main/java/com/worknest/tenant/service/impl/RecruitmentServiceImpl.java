package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.DuplicateEmailException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.storage.FileStorageService;
import com.worknest.common.storage.StorageCategory;
import com.worknest.common.storage.StoredFileDto;
import com.worknest.common.util.SlugUtils;
import com.worknest.master.entity.PlatformTenant;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.multitenancy.context.TenantContextHolder;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.employee.EmployeeCreateRequestDto;
import com.worknest.tenant.dto.employee.EmployeeResponseDto;
import com.worknest.tenant.dto.recruitment.*;
import com.worknest.tenant.entity.*;
import com.worknest.tenant.enums.*;
import com.worknest.tenant.repository.*;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.EmployeeService;
import com.worknest.tenant.service.NotificationService;
import com.worknest.tenant.service.RecruitmentService;
import com.worknest.tenant.service.RecruitmentEmailTemplateService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(transactionManager = "transactionManager")
public class RecruitmentServiceImpl implements RecruitmentService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RecruitmentServiceImpl.class);

    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String TEMP_PASSWORD_CHARSET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";

    private final JobPositionRepository jobPositionRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateCommentRepository candidateCommentRepository;
    private final CandidateApplicationRepository candidateApplicationRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final RecruitmentApplicationEventRepository applicationEventRepository;
    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final EmployeeService employeeService;
    private final NotificationService notificationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final FileStorageService fileStorageService;
    private final RecruitmentEmailTemplateService recruitmentEmailTemplateService;
    private final MasterTenantLookupService masterTenantLookupService;
    private final SecureRandom secureRandom;
    @Autowired(required = false)
    private TenantRealtimePublisher tenantRealtimePublisher;
    @Value("${app.public-web-base-url:http://localhost:5173}")
    private String publicWebBaseUrl;

    public RecruitmentServiceImpl(
            JobPositionRepository jobPositionRepository,
            CandidateRepository candidateRepository,
            CandidateCommentRepository candidateCommentRepository,
            CandidateApplicationRepository candidateApplicationRepository,
            InterviewRepository interviewRepository,
            InterviewFeedbackRepository interviewFeedbackRepository,
            RecruitmentApplicationEventRepository applicationEventRepository,
            EmployeeRepository employeeRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            AuthorizationService authorizationService,
            AuditLogService auditLogService,
            EmployeeService employeeService,
            NotificationService notificationService,
            TenantDtoMapper tenantDtoMapper,
            FileStorageService fileStorageService,
            RecruitmentEmailTemplateService recruitmentEmailTemplateService,
            MasterTenantLookupService masterTenantLookupService) {
        this.jobPositionRepository = jobPositionRepository;
        this.candidateRepository = candidateRepository;
        this.candidateCommentRepository = candidateCommentRepository;
        this.candidateApplicationRepository = candidateApplicationRepository;
        this.interviewRepository = interviewRepository;
        this.interviewFeedbackRepository = interviewFeedbackRepository;
        this.applicationEventRepository = applicationEventRepository;
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.employeeService = employeeService;
        this.notificationService = notificationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.fileStorageService = fileStorageService;
        this.recruitmentEmailTemplateService = recruitmentEmailTemplateService;
        this.masterTenantLookupService = masterTenantLookupService;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public JobPositionResponseDto createJobPosition(JobPositionCreateRequestDto requestDto) {
        requireManagePermission();
        JobPosition position = new JobPosition();
        applyJobPosition(
                position,
                requestDto.getTitle(),
                requestDto.getDepartment(),
                requestDto.getSummary(),
                requestDto.getDescription(),
                requestDto.getResponsibilities(),
                requestDto.getRequirements(),
                requestDto.getBenefits(),
                requestDto.getEmploymentType(),
                requestDto.getLocation(),
                requestDto.getExperience(),
                requestDto.getSalary(),
                requestDto.getOpenings(),
                requestDto.getStatus(),
                requestDto.getPublished(),
                requestDto.getVisibleToExternalApplicants(),
                requestDto.getExpiresAt());
        ensureJobSlug(position);
        if (position.isPublished()) {
            validateJobCanBePublished(position);
            position.setPublishedAt(LocalDateTime.now());
        }
        JobPosition saved = jobPositionRepository.save(position);
        publishRecruitmentRealtime("JOB_CREATED", saved.getId());
        auditLogService.logAction(AuditActionType.CREATE, AuditEntityType.JOB_POSITION, saved.getId(), jsonField("title", saved.getTitle()));
        return toJobPositionResponse(saved);
    }

    @Override
    public JobPositionResponseDto updateJobPosition(Long jobPositionId, JobPositionUpdateRequestDto requestDto) {
        requireManagePermission();
        JobPosition position = getJobPositionOrThrow(jobPositionId);
        boolean wasPublished = position.isPublished();
        String existingSummary = position.getSummary();
        String existingResponsibilities = position.getResponsibilities();
        String existingRequirements = position.getRequirements();
        String existingBenefits = position.getBenefits();
        String existingExperience = position.getExperience();
        String existingSalary = position.getSalary();
        applyJobPosition(
                position,
                requestDto.getTitle(),
                requestDto.getDepartment(),
                requestDto.getSummary(),
                requestDto.getDescription(),
                requestDto.getResponsibilities(),
                requestDto.getRequirements(),
                requestDto.getBenefits(),
                requestDto.getEmploymentType(),
                requestDto.getLocation(),
                requestDto.getExperience(),
                requestDto.getSalary(),
                requestDto.getOpenings(),
                requestDto.getStatus(),
                requestDto.getPublished(),
                requestDto.getVisibleToExternalApplicants(),
                requestDto.getExpiresAt());
        if (requestDto.getSummary() == null) position.setSummary(existingSummary);
        if (requestDto.getResponsibilities() == null) position.setResponsibilities(existingResponsibilities);
        if (requestDto.getRequirements() == null) position.setRequirements(existingRequirements);
        if (requestDto.getBenefits() == null) position.setBenefits(existingBenefits);
        if (requestDto.getExperience() == null) position.setExperience(existingExperience);
        if (requestDto.getSalary() == null) position.setSalary(existingSalary);
        ensureJobSlug(position);
        if (!wasPublished && position.isPublished()) {
            validateJobCanBePublished(position);
            position.setPublishedAt(LocalDateTime.now());
        }
        JobPosition updated = jobPositionRepository.save(position);
        publishRecruitmentRealtime("JOB_UPDATED", updated.getId());
        auditLogService.logAction(AuditActionType.UPDATE, AuditEntityType.JOB_POSITION, updated.getId(), jsonField("title", updated.getTitle()));
        return toJobPositionResponse(updated);
    }

    @Override
    public JobPositionResponseDto publishJobPosition(Long jobPositionId) {
        requireManagePermission();
        JobPosition position = getJobPositionOrThrow(jobPositionId);
        position.setStatus(JobPositionStatus.OPEN);
        position.setVisibleToExternalApplicants(true);
        validateJobCanBePublished(position);
        position.setPublished(true);
        position.setPublishedAt(LocalDateTime.now());
        return saveJobAction(position, "JOB_PUBLISHED", "published");
    }

    @Override
    public JobPositionResponseDto unpublishJobPosition(Long jobPositionId) {
        requireManagePermission();
        JobPosition position = getJobPositionOrThrow(jobPositionId);
        position.setPublished(false);
        return saveJobAction(position, "JOB_UNPUBLISHED", "unpublished");
    }

    @Override
    public JobPositionResponseDto closeJobPosition(Long jobPositionId) {
        requireManagePermission();
        JobPosition position = getJobPositionOrThrow(jobPositionId);
        position.setStatus(JobPositionStatus.CLOSED);
        position.setPublished(false);
        return saveJobAction(position, "JOB_CLOSED", "closed");
    }

    @Override
    public JobPositionResponseDto reopenJobPosition(Long jobPositionId) {
        requireManagePermission();
        JobPosition position = getJobPositionOrThrow(jobPositionId);
        position.setStatus(JobPositionStatus.OPEN);
        return saveJobAction(position, "JOB_REOPENED", "reopened");
    }

    @Override
    public JobPositionResponseDto duplicateJobPosition(Long jobPositionId) {
        requireManagePermission();
        JobPosition source = getJobPositionOrThrow(jobPositionId);
        JobPosition duplicate = new JobPosition();
        duplicate.setTitle("Copy of " + source.getTitle());
        duplicate.setDepartment(source.getDepartment());
        duplicate.setSummary(source.getSummary());
        duplicate.setDescription(source.getDescription());
        duplicate.setResponsibilities(source.getResponsibilities());
        duplicate.setRequirements(source.getRequirements());
        duplicate.setBenefits(source.getBenefits());
        duplicate.setEmploymentType(source.getEmploymentType());
        duplicate.setLocation(source.getLocation());
        duplicate.setExperience(source.getExperience());
        duplicate.setSalary(source.getSalary());
        duplicate.setOpenings(source.getOpenings());
        duplicate.setStatus(JobPositionStatus.OPEN);
        duplicate.setPublished(false);
        duplicate.setVisibleToExternalApplicants(true);
        duplicate.setExpiresAt(source.getExpiresAt());
        ensureJobSlug(duplicate);
        JobPosition saved = jobPositionRepository.save(duplicate);
        publishRecruitmentRealtime("JOB_DUPLICATED", saved.getId());
        auditLogService.logAction(AuditActionType.CREATE, AuditEntityType.JOB_POSITION, saved.getId(), jsonField("duplicatedFrom", String.valueOf(source.getId())));
        return toJobPositionResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public JobPositionResponseDto getJobPositionById(Long jobPositionId) {
        requireViewPermission();
        return toJobPositionResponse(getJobPositionOrThrow(jobPositionId));
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<JobPositionResponseDto> listJobPositions(String search, int page, int size, String sortBy, String sortDir) {
        requireViewPermission();
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isJobSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<JobPosition> resultPage = jobPositionRepository.searchActive(
                trimToEmpty(search), PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy)));
        return toPagedResult(resultPage.map(this::toJobPositionResponse));
    }

    @Override
    public void deleteJobPosition(Long jobPositionId) {
        requireManagePermission();
        JobPosition position = getJobPositionOrThrow(jobPositionId);
        position.setDeleted(true);
        position.setPublished(false);
        position.setStatus(JobPositionStatus.CLOSED);
        jobPositionRepository.save(position);
        auditLogService.logAction(AuditActionType.DELETE, AuditEntityType.JOB_POSITION, jobPositionId, jsonField("title", position.getTitle()));
    }

    @Override
    public CandidateResponseDto createCandidate(CandidateCreateRequestDto requestDto) {
        requireManagePermission();
        String email = normalizeEmail(requestDto.getEmail());
        if (candidateRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException("Candidate already exists: " + email);
        }
        Candidate candidate = new Candidate();
        applyCandidate(candidate, requestDto.getFullName(), email, requestDto.getPhone(), requestDto.getCurrentTitle(), requestDto.getYearsOfExperience(), requestDto.getSource(), requestDto.getSummary());
        Candidate saved = candidateRepository.save(candidate);
        auditLogService.logAction(AuditActionType.CREATE, AuditEntityType.CANDIDATE, saved.getId(), jsonField("email", saved.getEmail()));
        return toCandidateResponse(saved);
    }

    @Override
    public CandidateResponseDto updateCandidate(Long candidateId, CandidateUpdateRequestDto requestDto) {
        requireManagePermission();
        Candidate candidate = getCandidateOrThrow(candidateId);
        String email = normalizeEmail(requestDto.getEmail());
        if (!candidate.getEmail().equalsIgnoreCase(email) && candidateRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException("Candidate already exists: " + email);
        }
        applyCandidate(candidate, requestDto.getFullName(), email, requestDto.getPhone(), requestDto.getCurrentTitle(), requestDto.getYearsOfExperience(), requestDto.getSource(), requestDto.getSummary());
        Candidate updated = candidateRepository.save(candidate);
        auditLogService.logAction(AuditActionType.UPDATE, AuditEntityType.CANDIDATE, updated.getId(), jsonField("email", updated.getEmail()));
        return toCandidateResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public CandidateResponseDto getCandidateById(Long candidateId) {
        requireViewPermission();
        return toCandidateResponse(getCandidateOrThrow(candidateId));
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<CandidateResponseDto> listCandidates(String search, int page, int size, String sortBy, String sortDir) {
        requireViewPermission();
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isCandidateSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<Candidate> resultPage = candidateRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrCurrentTitleContainingIgnoreCase(
                trimToEmpty(search), trimToEmpty(search), trimToEmpty(search), trimToEmpty(search), PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy)));
        return toPagedResult(resultPage.map(this::toCandidateResponse));
    }

    @Override
    public CandidateResponseDto uploadCandidateResume(Long candidateId, MultipartFile resumeFile) {
        requireManagePermission();
        Candidate candidate = getCandidateOrThrow(candidateId);
        String previousResume = candidate.getResumeFileUrl();
        StoredFileDto storedFile = fileStorageService.store(resumeFile, StorageCategory.CANDIDATE_RESUME);
        String newReference = "wnfileid://" + storedFile.id();
        registerResumeCleanup(newReference, previousResume);
        candidate.setResumeFileName(storedFile.originalName());
        candidate.setResumeFileUrl(newReference);
        candidate.setResumeMimeType(storedFile.contentType());
        candidate.setResumeFileSizeBytes(storedFile.size());
        Candidate updated = candidateRepository.save(candidate);
        fileStorageService.link(newReference, "CANDIDATE", updated.getId(), StorageCategory.CANDIDATE_RESUME);
        auditLogService.logAction(AuditActionType.UPLOAD, AuditEntityType.CANDIDATE, updated.getId(), jsonField("resume", updated.getResumeFileName()));
        return toCandidateResponse(updated);
    }

    private void registerResumeCleanup(String newReference, String previousReference) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        String tenantSlug = TenantContextHolder.getTenantSlug();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    if (fileStorageService.isLocalReference(previousReference)) {
                        deleteResumeSafely(tenantSlug, previousReference);
                    }
                } else {
                    deleteResumeSafely(tenantSlug, newReference);
                }
            }
        });
    }

    private void deleteResumeSafely(String tenantSlug, String reference) {
        try {
            fileStorageService.delete(tenantSlug, reference);
        } catch (RuntimeException exception) {
            log.warn("Unable to clean up recruitment resume {} for tenant {}", reference, tenantSlug, exception);
        }
    }

    @Override
    public CandidateCommentResponseDto addCandidateComment(CandidateCommentCreateRequestDto requestDto) {
        requireManagePermission();
        Candidate candidate = getCandidateOrThrow(requestDto.getCandidateId());
        CandidateComment comment = new CandidateComment();
        comment.setCandidate(candidate);
        comment.setMessage(requestDto.getMessage().trim());
        comment.setAuthor(authorizationService.getCurrentEmployeeOrNull());
        CandidateComment saved = candidateCommentRepository.save(comment);
        auditLogService.logAction(AuditActionType.CREATE, AuditEntityType.CANDIDATE_COMMENT, saved.getId(), jsonField("candidateId", String.valueOf(candidate.getId())));
        return toCandidateCommentResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<CandidateCommentResponseDto> listCandidateComments(Long candidateId) {
        requireViewPermission();
        getCandidateOrThrow(candidateId);
        return candidateCommentRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream().map(this::toCandidateCommentResponse).toList();
    }

    @Override
    public CandidateApplicationResponseDto createApplication(CandidateApplicationCreateRequestDto requestDto) {
        requireManagePermission();
        Candidate candidate = getCandidateOrThrow(requestDto.getCandidateId());
        JobPosition position = getJobPositionOrThrow(requestDto.getJobPositionId());
        if (candidateApplicationRepository.existsByCandidateIdAndJobPositionId(candidate.getId(), position.getId())) {
            throw new BadRequestException("Candidate already has an application for this job position");
        }
        CandidatePipelineStatus requestedStatus = requestDto.getStatus() == null
                ? CandidatePipelineStatus.APPLIED
                : requestDto.getStatus();
        if (requestedStatus == CandidatePipelineStatus.HIRED) {
            throw new BadRequestException("Use the hire workflow to mark an application as hired");
        }
        CandidateApplication application = new CandidateApplication();
        application.setCandidate(candidate);
        application.setJobPosition(position);
        application.setCoverLetter(trimToNull(requestDto.getCoverLetter()));
        application.setExpectedSalary(requestDto.getExpectedSalary());
        application.setStatus(requestedStatus);
        application.setCreatedBy(authorizationService.getCurrentEmployeeOrNull());
        CandidateApplication saved = candidateApplicationRepository.save(application);
        addApplicationEvent(saved, "APPLIED", "Application created", "Application added by HR");
        publishRecruitmentRealtime("APPLICATION_CREATED", saved.getId());
        auditLogService.logAction(AuditActionType.CREATE, AuditEntityType.CANDIDATE_APPLICATION, saved.getId(), jsonField("candidateId", String.valueOf(candidate.getId())));
        return toApplicationResponse(saved);
    }

    @Override
    public CandidateApplicationResponseDto updateApplication(Long applicationId, CandidateApplicationUpdateRequestDto requestDto) {
        requireManagePermission();
        CandidateApplication application = getApplicationOrThrow(applicationId);
        CandidatePipelineStatus previousStatus = application.getStatus();
        applyApplicationUpdates(application, requestDto);
        CandidateApplication updated = candidateApplicationRepository.save(application);
        if (previousStatus != updated.getStatus()) {
            addApplicationEvent(updated, "STAGE_CHANGED", "Moved to " + toDisplayStage(updated.getStatus()),
                    "Previous stage: " + toDisplayStage(previousStatus));
        }
        publishRecruitmentRealtime("APPLICATION_UPDATED", updated.getId());
        auditLogService.logAction(AuditActionType.UPDATE, AuditEntityType.CANDIDATE_APPLICATION, updated.getId(), jsonField("status", updated.getStatus().name()));
        return toApplicationResponse(updated);
    }

    @Override
    public CandidateApplicationResponseDto updateApplicationStatus(Long applicationId, CandidateApplicationUpdateRequestDto requestDto) {
        return updateApplication(applicationId, requestDto);
    }

    @Override
    public CandidateCommentResponseDto addApplicationNote(Long applicationId, RecruitmentApplicationNoteRequestDto requestDto) {
        requireManagePermission();
        CandidateApplication application = getApplicationOrThrow(applicationId);
        ensureApplicationEditable(application);
        CandidateComment note = new CandidateComment();
        note.setCandidate(application.getCandidate());
        note.setApplication(application);
        note.setMessage(requestDto.getMessage().trim());
        note.setAuthor(authorizationService.getCurrentEmployeeOrNull());
        CandidateComment saved = candidateCommentRepository.save(note);
        addApplicationEvent(application, "NOTE_ADDED", "Internal note added", null);
        return toCandidateCommentResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<CandidateCommentResponseDto> listApplicationNotes(Long applicationId) {
        requireViewPermission();
        getApplicationOrThrow(applicationId);
        return candidateCommentRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(this::toCandidateCommentResponse).toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<RecruitmentApplicationEventResponseDto> listApplicationTimeline(Long applicationId) {
        requireViewPermission();
        CandidateApplication application = getApplicationOrThrow(applicationId);
        List<RecruitmentApplicationEventResponseDto> events = applicationEventRepository
                .findByApplicationIdOrderByOccurredAtDesc(applicationId).stream()
                .map(this::toApplicationEventResponse).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (events.isEmpty()) {
            events.add(RecruitmentApplicationEventResponseDto.builder()
                    .id(0L).eventType("APPLIED").title("Application received")
                    .detail(application.getReferenceNumber()).occurredAt(application.getAppliedAt()).build());
        }
        return events;
    }

    @Override
    public RecruitmentEmailLogResponseDto sendApplicationEmail(Long applicationId, RecruitmentSendEmailRequestDto requestDto) {
        requireManagePermission();
        CandidateApplication application = getApplicationOrThrow(applicationId);
        Interview interview = interviewRepository.findByApplicationIdOrderByScheduledAtDesc(applicationId).stream().findFirst().orElse(null);
        TenantBrand tenant = resolveTenantBrand();
        RecruitmentEmailLogResponseDto sent = recruitmentEmailTemplateService.send(
                application, requestDto.getTemplateType(), tenant.companyName(), tenant.careersLink(), interview);
        addApplicationEvent(application, "EMAIL_SENT", "Email queued", toLabel(requestDto.getTemplateType().name()));
        return sent;
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<RecruitmentEmailLogResponseDto> listApplicationEmails(Long applicationId) {
        requireViewPermission();
        getApplicationOrThrow(applicationId);
        return recruitmentEmailTemplateService.listApplicationEmails(applicationId);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<InterviewResponseDto> listApplicationInterviews(Long applicationId) {
        requireViewPermission();
        getApplicationOrThrow(applicationId);
        return toInterviewResponses(interviewRepository.findByApplicationIdOrderByScheduledAtDesc(applicationId));
    }

    @Override
    public RecruitmentHireResponseDto hireApplication(Long applicationId, RecruitmentHireRequestDto requestDto) {
        requireManagePermission();
        CandidateApplication application = getApplicationOrThrow(applicationId);
        validateApplicationCanBeHired(application);

        Candidate candidate = application.getCandidate();
        String candidateEmail = normalizeEmail(candidate.getEmail());
        if (employeeRepository.existsByEmailIgnoreCase(candidateEmail)) {
            throw new DuplicateEmailException("Employee email already exists in this tenant: " + candidateEmail);
        }

        String requestedPassword = trimToNull(requestDto.getTemporaryPassword());
        String temporaryPassword = requestedPassword == null ? generateTemporaryPassword() : requestedPassword;

        EmployeeCreateRequestDto employeeRequest = buildEmployeeCreateRequest(application, requestDto, temporaryPassword);
        EmployeeResponseDto employeeResponse = employeeService.createEmployee(employeeRequest);
        Employee createdEmployee = employeeRepository.findById(employeeResponse.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found after hire conversion"));

        Team assignedTeam = assignHiredEmployeeToTeam(createdEmployee, requestDto);

        application.setStatus(CandidatePipelineStatus.HIRED);
        application.setHiredAt(LocalDateTime.now());
        application.setHiredEmployee(createdEmployee);
        if (trimToNull(requestDto.getRecruiterNotes()) != null) {
            application.setRecruiterNotes(trimToNull(requestDto.getRecruiterNotes()));
        }
        CandidateApplication updatedApplication = candidateApplicationRepository.save(application);
        addApplicationEvent(updatedApplication, "HIRED", "Converted to employee",
                "Employee code: " + createdEmployee.getEmployeeCode());
        JobPosition hiredJob = updatedApplication.getJobPosition();
        long hiresForJob = candidateApplicationRepository.countByJobPositionIdAndStatus(
                hiredJob.getId(), CandidatePipelineStatus.HIRED);
        if (hiresForJob >= hiredJob.getOpenings()) {
            hiredJob.setStatus(JobPositionStatus.CLOSED);
            hiredJob.setPublished(false);
            jobPositionRepository.save(hiredJob);
        }
        publishRecruitmentRealtime("CANDIDATE_HIRED", updatedApplication.getId());

        notificationService.createSystemNotification(
                createdEmployee.getId(),
                NotificationType.SYSTEM,
                "Your employee account has been created from recruitment. You can now log in to WorkNest.",
                AuditEntityType.EMPLOYEE.name(),
                createdEmployee.getId()
        );
        TenantBrand tenant = resolveTenantBrand();
        recruitmentEmailTemplateService.send(updatedApplication, RecruitmentEmailTemplateType.WELCOME_EMPLOYEE,
                tenant.companyName(), tenant.careersLink(), null);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.CANDIDATE_APPLICATION,
                updatedApplication.getId(),
                "{\"hiredEmployeeId\":" + createdEmployee.getId() + ",\"teamId\":" + (assignedTeam == null ? "null" : assignedTeam.getId()) + "}"
        );

        return RecruitmentHireResponseDto.builder()
                .application(toApplicationResponse(updatedApplication))
                .employee(employeeResponse)
                .teamId(assignedTeam == null ? null : assignedTeam.getId())
                .teamName(assignedTeam == null ? null : assignedTeam.getName())
                .accountProvisioned(employeeResponse.isAccountProvisioned())
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public CandidateApplicationResponseDto getApplicationById(Long applicationId) {
        requireViewPermission();
        return toApplicationResponse(getApplicationOrThrow(applicationId));
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<CandidateApplicationResponseDto> listApplications(String search, CandidatePipelineStatus status, Long jobPositionId, int page, int size, String sortBy, String sortDir) {
        requireViewPermission();
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isApplicationSortable(sortBy) ? sortBy : "appliedAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<CandidateApplication> resultPage = candidateApplicationRepository.searchApplications(
                trimToEmpty(search), status, jobPositionId,
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy)));
        return toPagedResult(resultPage.map(this::toApplicationResponse));
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public RecruitmentPipelineResponseDto getPipeline(Long jobPositionId) {
        requireViewPermission();
        List<CandidateApplication> applications = jobPositionId == null
                ? candidateApplicationRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
                : candidateApplicationRepository.findByJobPositionIdOrderByAppliedAtDesc(jobPositionId);
        List<RecruitmentPipelineColumnDto> columns = new ArrayList<>();
        for (CandidatePipelineStatus stage : simpleStages()) {
            List<CandidateApplicationResponseDto> stageItems = applications.stream()
                    .filter(application -> canonicalStage(application.getStatus()) == stage)
                    .map(this::toApplicationResponse)
                    .toList();
            columns.add(RecruitmentPipelineColumnDto.builder()
                    .stage(stage)
                    .label(toLabel(stage.name()))
                    .count(stageItems.size())
                    .applications(stageItems)
                    .build());
        }
        return RecruitmentPipelineResponseDto.builder().columns(columns).build();
    }

    @Override
    public InterviewResponseDto scheduleInterview(InterviewScheduleRequestDto requestDto) {
        requireSchedulePermission();
        CandidateApplication application = getApplicationOrThrow(requestDto.getApplicationId());
        ensureApplicationEditable(application);
        CandidatePipelineStatus interviewStage = canonicalStage(application.getStatus());
        if (interviewStage != CandidatePipelineStatus.SHORTLISTED && interviewStage != CandidatePipelineStatus.INTERVIEW) {
            throw new BadRequestException("Application must be shortlisted before scheduling an interview");
        }
        validateInterviewDetails(requestDto);
        Employee interviewer = requestDto.getInterviewerEmployeeId() == null
                ? authorizationService.getCurrentEmployeeOrNull()
                : getEmployeeOrThrow(requestDto.getInterviewerEmployeeId());
        if (interviewer == null) {
            throw new BadRequestException("An interviewer is required to schedule an interview");
        }
        Interview interview = new Interview();
        interview.setApplication(application);
        interview.setInterviewer(interviewer);
        interview.setMode(requestDto.getMode());
        interview.setStatus(InterviewStatus.SCHEDULED);
        interview.setScheduledAt(requestDto.getScheduledAt());
        interview.setLocation(trimToNull(requestDto.getLocation()));
        interview.setMeetingLink(trimToNull(requestDto.getMeetingLink()));
        interview.setNotes(trimToNull(requestDto.getNotes()));
        Interview saved = interviewRepository.save(interview);
        if (application.getStatus() == CandidatePipelineStatus.SHORTLISTED
                || application.getStatus() == CandidatePipelineStatus.APPLIED
                || application.getStatus() == CandidatePipelineStatus.SCREENING) {
            CandidatePipelineStatus oldStatus = application.getStatus();
            application.setStatus(CandidatePipelineStatus.INTERVIEW);
            candidateApplicationRepository.save(application);
            addApplicationEvent(application, "STAGE_CHANGED", "Moved to Interview",
                    "Previous stage: " + toDisplayStage(oldStatus));
        }
        addApplicationEvent(application, "INTERVIEW_SCHEDULED", "Interview scheduled",
                saved.getScheduledAt().toString());
        publishRecruitmentRealtime("INTERVIEW_SCHEDULED", saved.getId());
        auditLogService.logAction(AuditActionType.SCHEDULE, AuditEntityType.INTERVIEW, saved.getId(), jsonField("applicationId", String.valueOf(application.getId())));
        TenantBrand tenant = resolveTenantBrand();
        recruitmentEmailTemplateService.send(application, RecruitmentEmailTemplateType.INTERVIEW_INVITATION,
                tenant.companyName(), tenant.careersLink(), saved);
        return toInterviewResponse(saved);
    }

    @Override
    public InterviewResponseDto updateInterview(Long interviewId, InterviewScheduleRequestDto requestDto) {
        requireSchedulePermission();
        Interview interview = getInterviewOrThrow(interviewId);
        ensureApplicationEditable(interview.getApplication());
        validateInterviewDetails(requestDto);
        Employee interviewer = requestDto.getInterviewerEmployeeId() == null
                ? interview.getInterviewer()
                : getEmployeeOrThrow(requestDto.getInterviewerEmployeeId());
        interview.setInterviewer(interviewer);
        interview.setMode(requestDto.getMode());
        interview.setScheduledAt(requestDto.getScheduledAt());
        interview.setLocation(trimToNull(requestDto.getLocation()));
        interview.setMeetingLink(trimToNull(requestDto.getMeetingLink()));
        interview.setNotes(trimToNull(requestDto.getNotes()));
        interview.setStatus(InterviewStatus.RESCHEDULED);
        Interview updated = interviewRepository.save(interview);
        addApplicationEvent(interview.getApplication(), "INTERVIEW_RESCHEDULED", "Interview rescheduled",
                updated.getScheduledAt().toString());
        publishRecruitmentRealtime("INTERVIEW_UPDATED", updated.getId());
        auditLogService.logAction(AuditActionType.UPDATE, AuditEntityType.INTERVIEW, updated.getId(), jsonField("scheduledAt", updated.getScheduledAt().toString()));
        TenantBrand tenant = resolveTenantBrand();
        recruitmentEmailTemplateService.send(interview.getApplication(), RecruitmentEmailTemplateType.INTERVIEW_RESCHEDULED,
                tenant.companyName(), tenant.careersLink(), updated);
        return toInterviewResponse(updated);
    }

    @Override
    public InterviewFeedbackResponseDto submitFeedback(InterviewFeedbackRequestDto requestDto) {
        requireManagePermission();
        Interview interview = getInterviewOrThrow(requestDto.getInterviewId());
        InterviewFeedback feedback = interviewFeedbackRepository.findByInterviewId(interview.getId()).orElseGet(InterviewFeedback::new);
        feedback.setInterview(interview);
        feedback.setReviewer(authorizationService.getCurrentEmployeeOrNull());
        feedback.setRating(requestDto.getRating());
        feedback.setRecommendation(requestDto.getRecommendation());
        feedback.setStrengths(trimToNull(requestDto.getStrengths()));
        feedback.setConcerns(trimToNull(requestDto.getConcerns()));
        feedback.setNotes(trimToNull(requestDto.getNotes()));
        InterviewFeedback saved = interviewFeedbackRepository.save(feedback);
        auditLogService.logAction(AuditActionType.SUBMIT_FEEDBACK, AuditEntityType.INTERVIEW_FEEDBACK, saved.getId(), jsonField("interviewId", String.valueOf(interview.getId())));
        return toInterviewFeedbackResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<InterviewResponseDto> listUpcomingInterviews(LocalDateTime from, LocalDateTime to) {
        requireViewPermission();
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(1) : from;
        LocalDateTime end = to == null ? LocalDateTime.now().plusDays(30) : to;
        return toInterviewResponses(interviewRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(start, end));
    }

    @Override
    public List<RecruitmentEmailTemplateResponseDto> listEmailTemplates() {
        requireViewPermission();
        return recruitmentEmailTemplateService.listTemplates();
    }

    @Override
    public RecruitmentEmailTemplateResponseDto updateEmailTemplate(
            RecruitmentEmailTemplateType type,
            RecruitmentEmailTemplateUpdateRequestDto requestDto) {
        requireManagePermission();
        RecruitmentEmailTemplateResponseDto updated = recruitmentEmailTemplateService.updateTemplate(type, requestDto);
        auditLogService.logAction(AuditActionType.UPDATE, AuditEntityType.RECRUITMENT_EMAIL_TEMPLATE, updated.getId(),
                jsonField("emailTemplate", type.name()));
        return updated;
    }

    private void publishRecruitmentRealtime(String eventType, Long entityId) {
        if (tenantRealtimePublisher != null) {
            tenantRealtimePublisher.publishRecruitmentUpdate(
                    authorizationService.getCurrentTenantKeyOrThrow(),
                    java.util.Map.of("eventType", eventType, "entityId", entityId)
            );
        }
    }

    private void requireViewPermission() {
        authorizationService.requirePermission(Permission.VIEW_RECRUITMENT);
    }

    private void requireManagePermission() {
        authorizationService.requirePermission(Permission.MANAGE_RECRUITMENT);
    }

    private void requireSchedulePermission() {
        authorizationService.requirePermission(Permission.SCHEDULE_INTERVIEW);
    }

    private JobPosition getJobPositionOrThrow(Long id) {
        JobPosition position = jobPositionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job position not found with id: " + id));
        if (Boolean.TRUE.equals(position.getDeleted())) {
            throw new ResourceNotFoundException("Job position not found with id: " + id);
        }
        return position;
    }

    private Candidate getCandidateOrThrow(Long id) {
        return candidateRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Candidate not found with id: " + id));
    }

    private CandidateApplication getApplicationOrThrow(Long id) {
        return candidateApplicationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
    }

    private Interview getInterviewOrThrow(Long id) {
        return interviewRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Interview not found with id: " + id));
    }

    private Employee getEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }

    private void applyJobPosition(
            JobPosition position,
            String title,
            String department,
            String summary,
            String description,
            String responsibilities,
            String requirements,
            String benefits,
            EmploymentType employmentType,
            String location,
            String experience,
            String salary,
            Integer openings,
            JobPositionStatus status,
            Boolean published,
            Boolean visibleToExternalApplicants,
            LocalDateTime expiresAt) {
        position.setTitle(title.trim());
        position.setDepartment(trimToNull(department));
        position.setSummary(trimToNull(summary));
        position.setDescription(trimToNull(description));
        position.setResponsibilities(trimToNull(responsibilities));
        position.setRequirements(trimToNull(requirements));
        position.setBenefits(trimToNull(benefits));
        position.setEmploymentType(employmentType);
        position.setLocation(trimToNull(location));
        position.setExperience(trimToNull(experience));
        position.setSalary(trimToNull(salary));
        position.setOpenings(openings);
        position.setStatus(status == null ? JobPositionStatus.OPEN : status);
        position.setPublished(Boolean.TRUE.equals(published));
        if (visibleToExternalApplicants != null) {
            position.setVisibleToExternalApplicants(visibleToExternalApplicants);
        } else if (position.getVisibleToExternalApplicants() == null) {
            position.setVisibleToExternalApplicants(true);
        }
        position.setExpiresAt(expiresAt);
    }

    private JobPositionResponseDto saveJobAction(JobPosition position, String realtimeEvent, String auditAction) {
        JobPosition saved = jobPositionRepository.save(position);
        publishRecruitmentRealtime(realtimeEvent, saved.getId());
        auditLogService.logAction(AuditActionType.UPDATE, AuditEntityType.JOB_POSITION, saved.getId(),
                jsonField("action", auditAction));
        return toJobPositionResponse(saved);
    }

    private void validateJobCanBePublished(JobPosition position) {
        if (trimToNull(position.getDepartment()) == null) {
            throw new BadRequestException("Department is required before publishing a job opening");
        }
        if (trimToNull(position.getDescription()) == null) {
            throw new BadRequestException("A markdown job description is required before publishing");
        }
        if (position.getExpiresAt() != null && !position.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Application deadline must be in the future");
        }
        if (position.getStatus() == JobPositionStatus.CLOSED) {
            throw new BadRequestException("Reopen the job opening before publishing it");
        }
    }

    private void ensureJobSlug(JobPosition position) {
        if (trimToNull(position.getSlug()) != null) {
            return;
        }

        String baseSlug = SlugUtils.slugify(position.getTitle());
        if (baseSlug == null) {
            baseSlug = "job-position";
        }

        String candidate = baseSlug;
        int suffix = 2;
        while (jobPositionRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }
        position.setSlug(candidate);
    }

    private void applyCandidate(Candidate candidate, String fullName, String email, String phone, String currentTitle, Integer yearsOfExperience, String source, String summary) {
        candidate.setFullName(fullName.trim());
        candidate.setEmail(email);
        candidate.setPhone(trimToNull(phone));
        candidate.setCurrentTitle(trimToNull(currentTitle));
        candidate.setYearsOfExperience(yearsOfExperience);
        candidate.setSource(trimToNull(source));
        candidate.setSummary(trimToNull(summary));
    }

    private void applyApplicationUpdates(CandidateApplication application, CandidateApplicationUpdateRequestDto requestDto) {
        ensureApplicationEditable(application);
        CandidatePipelineStatus nextStatus = requestDto.getStatus();
        if (nextStatus == CandidatePipelineStatus.HIRED) {
            throw new BadRequestException("Use the hire workflow to create the employee account and mark this application as hired");
        }
        validateSimpleTransition(application.getStatus(), nextStatus);
        application.setStatus(nextStatus);
        if (requestDto.getRecruiterNotes() != null) {
            application.setRecruiterNotes(trimToNull(requestDto.getRecruiterNotes()));
        }
        if (requestDto.getExpectedSalary() != null) {
            application.setExpectedSalary(requestDto.getExpectedSalary());
        }
        if (nextStatus == CandidatePipelineStatus.REJECTED) {
            String reason = trimToNull(requestDto.getRejectedReason());
            if (reason == null) {
                throw new BadRequestException("A rejection reason is required");
            }
            application.setRejectedReason(reason);
        } else if (requestDto.getRejectedReason() != null) {
            application.setRejectedReason(trimToNull(requestDto.getRejectedReason()));
        }
        if (nextStatus == CandidatePipelineStatus.OFFERED && application.getOfferedAt() == null) {
            application.setOfferedAt(LocalDateTime.now());
        }
    }

    private void ensureApplicationEditable(CandidateApplication application) {
        if (application.getStatus() == CandidatePipelineStatus.HIRED || application.getHiredEmployee() != null) {
            throw new BadRequestException("Hired applications are read-only after employee conversion");
        }
    }

    private void validateSimpleTransition(CandidatePipelineStatus current, CandidatePipelineStatus next) {
        CandidatePipelineStatus from = canonicalStage(current);
        CandidatePipelineStatus to = canonicalStage(next);
        if (!simpleStages().contains(to)) {
            throw new BadRequestException("Only Applied, Shortlisted, Interview, Offer, Hired, and Rejected stages are supported");
        }
        if (from == to) return;
        boolean allowed = switch (from) {
            case APPLIED -> to == CandidatePipelineStatus.SHORTLISTED || to == CandidatePipelineStatus.REJECTED;
            case SHORTLISTED -> to == CandidatePipelineStatus.APPLIED || to == CandidatePipelineStatus.INTERVIEW || to == CandidatePipelineStatus.REJECTED;
            case INTERVIEW -> to == CandidatePipelineStatus.SHORTLISTED || to == CandidatePipelineStatus.OFFERED || to == CandidatePipelineStatus.REJECTED;
            case OFFERED -> to == CandidatePipelineStatus.INTERVIEW || to == CandidatePipelineStatus.REJECTED;
            default -> false;
        };
        if (!allowed) {
            throw new BadRequestException("Application cannot move from " + toDisplayStage(from) + " to " + toDisplayStage(to));
        }
    }

    private List<CandidatePipelineStatus> simpleStages() {
        return List.of(
                CandidatePipelineStatus.APPLIED,
                CandidatePipelineStatus.SHORTLISTED,
                CandidatePipelineStatus.INTERVIEW,
                CandidatePipelineStatus.OFFERED,
                CandidatePipelineStatus.HIRED,
                CandidatePipelineStatus.REJECTED);
    }

    private CandidatePipelineStatus canonicalStage(CandidatePipelineStatus status) {
        if (status == null) return CandidatePipelineStatus.APPLIED;
        return switch (status) {
            case SCREENING -> CandidatePipelineStatus.SHORTLISTED;
            case TECHNICAL, HR_REVIEW -> CandidatePipelineStatus.INTERVIEW;
            case WITHDRAWN -> CandidatePipelineStatus.REJECTED;
            default -> status;
        };
    }

    private String toDisplayStage(CandidatePipelineStatus status) {
        CandidatePipelineStatus canonical = canonicalStage(status);
        return canonical == CandidatePipelineStatus.OFFERED ? "Offer" : toLabel(canonical.name());
    }

    private void validateInterviewDetails(InterviewScheduleRequestDto requestDto) {
        if (requestDto.getScheduledAt() == null || !requestDto.getScheduledAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Interview date and time must be in the future");
        }
        if (requestDto.getMode() == InterviewMode.REMOTE && trimToNull(requestDto.getMeetingLink()) == null) {
            throw new BadRequestException("Meeting link is required for an online interview");
        }
        if (requestDto.getMode() == InterviewMode.ONSITE && trimToNull(requestDto.getLocation()) == null) {
            throw new BadRequestException("Location is required for a physical interview");
        }
    }

    private void addApplicationEvent(
            CandidateApplication application,
            String eventType,
            String title,
            String detail) {
        RecruitmentApplicationEvent event = new RecruitmentApplicationEvent();
        event.setApplication(application);
        event.setEventType(eventType);
        event.setTitle(title);
        event.setDetail(trimToNull(detail));
        event.setActor(authorizationService.getCurrentEmployeeOrNull());
        applicationEventRepository.save(event);
    }

    private RecruitmentApplicationEventResponseDto toApplicationEventResponse(RecruitmentApplicationEvent event) {
        return RecruitmentApplicationEventResponseDto.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .title(event.getTitle())
                .detail(event.getDetail())
                .actor(tenantDtoMapper.toEmployeeSimple(event.getActor()))
                .occurredAt(event.getOccurredAt())
                .build();
    }

    private TenantBrand resolveTenantBrand() {
        String tenantKey = authorizationService.getCurrentTenantKeyOrThrow();
        PlatformTenant tenant = masterTenantLookupService.findByTenantKey(tenantKey).orElse(null);
        String slug = tenant == null ? tenantKey : tenant.getSlug();
        String name = tenant == null ? tenantKey : tenant.getCompanyName();
        String base = publicWebBaseUrl == null ? "" : publicWebBaseUrl.replaceAll("/+$", "");
        return new TenantBrand(name, base + "/" + slug + "/careers");
    }

    private void validateApplicationCanBeHired(CandidateApplication application) {
        if (application.getStatus() == CandidatePipelineStatus.HIRED || application.getHiredAt() != null) {
            throw new BadRequestException("Candidate application is already hired");
        }
        if (application.getStatus() == CandidatePipelineStatus.REJECTED) {
            throw new BadRequestException("Rejected applications cannot be hired without first reopening the recruitment decision");
        }
        if (canonicalStage(application.getStatus()) != CandidatePipelineStatus.OFFERED) {
            throw new BadRequestException("Application must reach the offer stage before it can be converted to an employee");
        }
        if (!interviewRepository.existsByApplicationId(application.getId())) {
            throw new BadRequestException("At least one interview must be scheduled before hiring a candidate");
        }
        if (candidateApplicationRepository.existsByCandidateIdAndStatusAndIdNot(
                application.getCandidate().getId(),
                CandidatePipelineStatus.HIRED,
                application.getId())) {
            throw new BadRequestException("Candidate is already hired through another application");
        }
    }

    private EmployeeCreateRequestDto buildEmployeeCreateRequest(
            CandidateApplication application,
            RecruitmentHireRequestDto requestDto,
            String temporaryPassword) {
        Candidate candidate = application.getCandidate();
        JobPosition jobPosition = application.getJobPosition();
        String[] nameParts = splitFullName(candidate.getFullName());

        EmployeeCreateRequestDto employeeRequest = new EmployeeCreateRequestDto();
        employeeRequest.setEmployeeCode(trimToNull(requestDto.getEmployeeCode()));
        employeeRequest.setFirstName(nameParts[0]);
        employeeRequest.setLastName(nameParts[1]);
        employeeRequest.setEmail(normalizeEmail(candidate.getEmail()));
        employeeRequest.setPassword(temporaryPassword);
        employeeRequest.setRole(resolveHireRole(requestDto.getRole()));
        employeeRequest.setDesignation(firstNonBlank(requestDto.getDesignation(), candidate.getCurrentTitle(), jobPosition.getTitle()));
        employeeRequest.setDepartment(firstNonBlank(requestDto.getDepartment(), jobPosition.getDepartment()));
        employeeRequest.setPhone(trimToNull(candidate.getPhone()));
        employeeRequest.setSalary(requestDto.getSalary() == null ? application.getExpectedSalary() : requestDto.getSalary());
        employeeRequest.setJoinedDate(requestDto.getJoinedDate() == null ? LocalDate.now() : requestDto.getJoinedDate());
        employeeRequest.setStatus(UserStatus.ACTIVE);
        return employeeRequest;
    }

    private Team assignHiredEmployeeToTeam(Employee employee, RecruitmentHireRequestDto requestDto) {
        if (requestDto.getTeamId() == null) {
            return null;
        }

        Team team = teamRepository.findById(requestDto.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + requestDto.getTeamId()));
        if (teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(team.getId(), employee.getId()).isPresent()) {
            throw new BadRequestException("Hired employee is already an active member of the selected team");
        }

        TeamMember teamMember = new TeamMember();
        teamMember.setTeam(team);
        teamMember.setEmployee(employee);
        teamMember.setFunctionalRole(requestDto.getTeamFunctionalRole() == null
                ? TeamFunctionalRole.MEMBER
                : requestDto.getTeamFunctionalRole());
        teamMemberRepository.save(teamMember);
        auditLogService.logAction(
                AuditActionType.ASSIGN,
                AuditEntityType.TEAM,
                team.getId(),
                "{\"employeeId\":" + employee.getId() + "}"
        );
        return team;
    }

    private PlatformRole resolveHireRole(PlatformRole role) {
        PlatformRole resolvedRole = role == null ? PlatformRole.EMPLOYEE : role;
        if (resolvedRole != PlatformRole.EMPLOYEE && resolvedRole != PlatformRole.MANAGER) {
            throw new BadRequestException("Recruitment conversion can assign only Employee or Manager roles");
        }
        return resolvedRole;
    }

    private String[] splitFullName(String fullName) {
        String normalized = trimToNull(fullName);
        if (normalized == null) {
            return new String[]{"New", "Employee"};
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 1) {
            return new String[]{parts[0], "Employee"};
        }
        String lastName = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        return new String[]{parts[0], lastName};
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            int index = secureRandom.nextInt(TEMP_PASSWORD_CHARSET.length());
            password.append(TEMP_PASSWORD_CHARSET.charAt(index));
        }
        return password.toString();
    }

    private CandidateApplicationResponseDto toApplicationResponse(CandidateApplication application) {
        return CandidateApplicationResponseDto.builder()
                .id(application.getId())
                .referenceNumber(application.getReferenceNumber())
                .candidate(toCandidateResponse(application.getCandidate()))
                .jobPosition(toJobPositionResponse(application.getJobPosition()))
                .status(application.getStatus())
                .coverLetter(application.getCoverLetter())
                .expectedSalary(application.getExpectedSalary())
                .availableFrom(application.getAvailableFrom())
                .source(application.getSource())
                .recruiterNotes(application.getRecruiterNotes())
                .rejectedReason(application.getRejectedReason())
                .createdBy(tenantDtoMapper.toEmployeeSimple(application.getCreatedBy()))
                .appliedAt(application.getAppliedAt())
                .updatedAt(application.getUpdatedAt())
                .offeredAt(application.getOfferedAt())
                .hiredAt(application.getHiredAt())
                .hiredEmployeeId(application.getHiredEmployee() == null ? null : application.getHiredEmployee().getId())
                .version(application.getVersion())
                .build();
    }

    private InterviewResponseDto toInterviewResponse(Interview interview) {
        InterviewFeedback feedback = interviewFeedbackRepository.findByInterviewId(interview.getId()).orElse(null);
        return toInterviewResponse(interview, feedback);
    }

    private List<InterviewResponseDto> toInterviewResponses(List<Interview> interviews) {
        if (interviews.isEmpty()) {
            return List.of();
        }

        Map<Long, InterviewFeedback> feedbackByInterviewId = new HashMap<>();
        interviewFeedbackRepository.findByInterviewIdIn(interviews.stream().map(Interview::getId).toList())
                .forEach(feedback -> feedbackByInterviewId.put(feedback.getInterview().getId(), feedback));

        return interviews.stream()
                .map(interview -> toInterviewResponse(interview, feedbackByInterviewId.get(interview.getId())))
                .toList();
    }

    private InterviewResponseDto toInterviewResponse(Interview interview, InterviewFeedback feedback) {
        return InterviewResponseDto.builder()
                .id(interview.getId())
                .applicationId(interview.getApplication().getId())
                .candidate(toCandidateResponse(interview.getApplication().getCandidate()))
                .jobPosition(toJobPositionResponse(interview.getApplication().getJobPosition()))
                .interviewer(tenantDtoMapper.toEmployeeSimple(interview.getInterviewer()))
                .mode(interview.getMode())
                .status(interview.getStatus())
                .scheduledAt(interview.getScheduledAt())
                .location(interview.getLocation())
                .meetingLink(interview.getMeetingLink())
                .notes(interview.getNotes())
                .feedback(feedback == null ? null : toInterviewFeedbackResponse(feedback))
                .createdAt(interview.getCreatedAt())
                .updatedAt(interview.getUpdatedAt())
                .build();
    }

    private InterviewFeedbackResponseDto toInterviewFeedbackResponse(InterviewFeedback feedback) {
        return InterviewFeedbackResponseDto.builder()
                .id(feedback.getId())
                .interviewId(feedback.getInterview().getId())
                .reviewer(tenantDtoMapper.toEmployeeSimple(feedback.getReviewer()))
                .rating(feedback.getRating())
                .recommendation(feedback.getRecommendation())
                .strengths(feedback.getStrengths())
                .concerns(feedback.getConcerns())
                .notes(feedback.getNotes())
                .createdAt(feedback.getCreatedAt())
                .updatedAt(feedback.getUpdatedAt())
                .build();
    }

    private CandidateCommentResponseDto toCandidateCommentResponse(CandidateComment comment) {
        return CandidateCommentResponseDto.builder()
                .id(comment.getId())
                .candidateId(comment.getCandidate().getId())
                .author(tenantDtoMapper.toEmployeeSimple(comment.getAuthor()))
                .message(comment.getMessage())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private CandidateResponseDto toCandidateResponse(Candidate candidate) {
        return CandidateResponseDto.builder()
                .id(candidate.getId())
                .fullName(candidate.getFullName())
                .email(candidate.getEmail())
                .phone(candidate.getPhone())
                .currentCity(candidate.getCurrentCity())
                .country(candidate.getCountry())
                .linkedinUrl(candidate.getLinkedinUrl())
                .portfolioUrl(candidate.getPortfolioUrl())
                .currentCompany(candidate.getCurrentCompany())
                .currentTitle(candidate.getCurrentTitle())
                .yearsOfExperience(candidate.getYearsOfExperience())
                .source(candidate.getSource())
                .summary(candidate.getSummary())
                .resumeFileName(candidate.getResumeFileName())
                .resumeFileUrl(fileStorageService.toPublicUrl(candidate.getResumeFileUrl()))
                .resumeMimeType(candidate.getResumeMimeType())
                .resumeFileSizeBytes(candidate.getResumeFileSizeBytes())
                .createdAt(candidate.getCreatedAt())
                .updatedAt(candidate.getUpdatedAt())
                .build();
    }

    private JobPositionResponseDto toJobPositionResponse(JobPosition position) {
        return JobPositionResponseDto.builder()
                .id(position.getId())
                .title(position.getTitle())
                .slug(position.getSlug())
                .department(position.getDepartment())
                .summary(position.getSummary())
                .description(position.getDescription())
                .responsibilities(position.getResponsibilities())
                .requirements(position.getRequirements())
                .benefits(position.getBenefits())
                .employmentType(position.getEmploymentType())
                .location(position.getLocation())
                .experience(position.getExperience())
                .salary(position.getSalary())
                .openings(position.getOpenings())
                .status(position.getStatus())
                .published(position.isPublished())
                .visibleToExternalApplicants(position.getVisibleToExternalApplicants())
                .expiresAt(position.getExpiresAt())
                .publishedAt(position.getPublishedAt())
                .applicationCount(candidateApplicationRepository.countByJobPositionId(position.getId()))
                .createdAt(position.getCreatedAt())
                .updatedAt(position.getUpdatedAt())
                .build();
    }

    private <T> PagedResultDto<T> toPagedResult(Page<T> page) {
        return PagedResultDto.<T>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String jsonField(String key, String value) {
        return "{\"" + key + "\":\"" + escapeJson(value) + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isJobSortable(String sortBy) {
        return "createdAt".equals(sortBy) || "updatedAt".equals(sortBy) || "title".equals(sortBy) || "status".equals(sortBy);
    }

    private boolean isCandidateSortable(String sortBy) {
        return "createdAt".equals(sortBy) || "updatedAt".equals(sortBy) || "fullName".equals(sortBy) || "email".equals(sortBy);
    }

    private boolean isApplicationSortable(String sortBy) {
        return "appliedAt".equals(sortBy) || "updatedAt".equals(sortBy) || "status".equals(sortBy);
    }

    private String toLabel(String name) {
        String value = name.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record TenantBrand(String companyName, String careersLink) {}
}
