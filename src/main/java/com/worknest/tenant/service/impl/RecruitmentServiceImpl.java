package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.DuplicateEmailException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.common.storage.FileStorageService;
import com.worknest.notification.email.EmailNotificationService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(transactionManager = "transactionManager")
public class RecruitmentServiceImpl implements RecruitmentService {

    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final String TEMP_PASSWORD_CHARSET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";

    private final JobPositionRepository jobPositionRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateCommentRepository candidateCommentRepository;
    private final CandidateApplicationRepository candidateApplicationRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final EmployeeService employeeService;
    private final NotificationService notificationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final FileStorageService fileStorageService;
    private final EmailNotificationService emailNotificationService;
    private final SecureRandom secureRandom;

    public RecruitmentServiceImpl(
            JobPositionRepository jobPositionRepository,
            CandidateRepository candidateRepository,
            CandidateCommentRepository candidateCommentRepository,
            CandidateApplicationRepository candidateApplicationRepository,
            InterviewRepository interviewRepository,
            InterviewFeedbackRepository interviewFeedbackRepository,
            EmployeeRepository employeeRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            AuthorizationService authorizationService,
            AuditLogService auditLogService,
            EmployeeService employeeService,
            NotificationService notificationService,
            TenantDtoMapper tenantDtoMapper,
            FileStorageService fileStorageService,
            EmailNotificationService emailNotificationService) {
        this.jobPositionRepository = jobPositionRepository;
        this.candidateRepository = candidateRepository;
        this.candidateCommentRepository = candidateCommentRepository;
        this.candidateApplicationRepository = candidateApplicationRepository;
        this.interviewRepository = interviewRepository;
        this.interviewFeedbackRepository = interviewFeedbackRepository;
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.employeeService = employeeService;
        this.notificationService = notificationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.fileStorageService = fileStorageService;
        this.emailNotificationService = emailNotificationService;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public JobPositionResponseDto createJobPosition(JobPositionCreateRequestDto requestDto) {
        requireManagePermission();
        JobPosition position = new JobPosition();
        applyJobPosition(position, requestDto.getTitle(), requestDto.getDepartment(), requestDto.getDescription(), requestDto.getEmploymentType(), requestDto.getLocation(), requestDto.getOpenings(), requestDto.getStatus(), requestDto.getPublished());
        JobPosition saved = jobPositionRepository.save(position);
        auditLogService.logAction(AuditActionType.CREATE, AuditEntityType.JOB_POSITION, saved.getId(), jsonField("title", saved.getTitle()));
        return toJobPositionResponse(saved);
    }

    @Override
    public JobPositionResponseDto updateJobPosition(Long jobPositionId, JobPositionUpdateRequestDto requestDto) {
        requireManagePermission();
        JobPosition position = getJobPositionOrThrow(jobPositionId);
        applyJobPosition(position, requestDto.getTitle(), requestDto.getDepartment(), requestDto.getDescription(), requestDto.getEmploymentType(), requestDto.getLocation(), requestDto.getOpenings(), requestDto.getStatus(), requestDto.getPublished());
        JobPosition updated = jobPositionRepository.save(position);
        auditLogService.logAction(AuditActionType.UPDATE, AuditEntityType.JOB_POSITION, updated.getId(), jsonField("title", updated.getTitle()));
        return toJobPositionResponse(updated);
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
        Page<JobPosition> resultPage = jobPositionRepository.findByTitleContainingIgnoreCaseOrDepartmentContainingIgnoreCase(
                trimToEmpty(search), trimToEmpty(search), PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy)));
        return toPagedResult(resultPage.map(this::toJobPositionResponse));
    }

    @Override
    public void deleteJobPosition(Long jobPositionId) {
        requireManagePermission();
        JobPosition position = getJobPositionOrThrow(jobPositionId);
        jobPositionRepository.delete(position);
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
        FileStorageService.StoredFileResult storedFileResult = fileStorageService.store(resumeFile, "doc");
        candidate.setResumeFileName(storedFileResult.name());
        candidate.setResumeFileUrl(storedFileResult.url());
        candidate.setResumeMimeType(storedFileResult.mimeType());
        candidate.setResumeFileSizeBytes(storedFileResult.size());
        Candidate updated = candidateRepository.save(candidate);
        auditLogService.logAction(AuditActionType.UPLOAD, AuditEntityType.CANDIDATE, updated.getId(), jsonField("resume", updated.getResumeFileName()));
        return toCandidateResponse(updated);
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
        auditLogService.logAction(AuditActionType.CREATE, AuditEntityType.CANDIDATE_APPLICATION, saved.getId(), jsonField("candidateId", String.valueOf(candidate.getId())));
        notifyCandidateStatus(saved);
        return toApplicationResponse(saved);
    }

    @Override
    public CandidateApplicationResponseDto updateApplication(Long applicationId, CandidateApplicationUpdateRequestDto requestDto) {
        requireManagePermission();
        CandidateApplication application = getApplicationOrThrow(applicationId);
        applyApplicationUpdates(application, requestDto);
        CandidateApplication updated = candidateApplicationRepository.save(application);
        auditLogService.logAction(AuditActionType.UPDATE, AuditEntityType.CANDIDATE_APPLICATION, updated.getId(), jsonField("status", updated.getStatus().name()));
        notifyCandidateStatus(updated);
        return toApplicationResponse(updated);
    }

    @Override
    public CandidateApplicationResponseDto updateApplicationStatus(Long applicationId, CandidateApplicationUpdateRequestDto requestDto) {
        return updateApplication(applicationId, requestDto);
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
        boolean generatedPassword = requestedPassword == null;
        String temporaryPassword = generatedPassword ? generateTemporaryPassword() : requestedPassword;

        EmployeeCreateRequestDto employeeRequest = buildEmployeeCreateRequest(application, requestDto, temporaryPassword);
        EmployeeResponseDto employeeResponse = employeeService.createEmployee(employeeRequest);
        Employee createdEmployee = employeeRepository.findById(employeeResponse.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found after hire conversion"));

        Team assignedTeam = assignHiredEmployeeToTeam(createdEmployee, requestDto);

        application.setStatus(CandidatePipelineStatus.HIRED);
        application.setHiredAt(LocalDateTime.now());
        if (trimToNull(requestDto.getRecruiterNotes()) != null) {
            application.setRecruiterNotes(trimToNull(requestDto.getRecruiterNotes()));
        }
        CandidateApplication updatedApplication = candidateApplicationRepository.save(application);

        notificationService.createSystemNotification(
                createdEmployee.getId(),
                NotificationType.SYSTEM,
                "Your employee account has been created from recruitment. You can now log in to WorkNest.",
                AuditEntityType.EMPLOYEE.name(),
                createdEmployee.getId()
        );
        notifyCandidateStatus(updatedApplication);
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
                .temporaryPassword(generatedPassword ? temporaryPassword : null)
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
        List<CandidateApplication> all = candidateApplicationRepository.findAll(Sort.by(direction, resolvedSortBy));
        List<CandidateApplication> filtered = all.stream()
                .filter(application -> status == null || application.getStatus() == status)
                .filter(application -> jobPositionId == null || Objects.equals(application.getJobPosition().getId(), jobPositionId))
                .filter(application -> {
                    String query = trimToEmpty(search).toLowerCase();
                    if (query.isBlank()) {
                        return true;
                    }
                    return contains(application.getCandidate().getFullName(), query)
                            || contains(application.getCandidate().getEmail(), query)
                            || contains(application.getJobPosition().getTitle(), query)
                            || contains(application.getStatus().name(), query);
                })
                .toList();
        int fromIndex = Math.min(resolvedPage * resolvedSize, filtered.size());
        int toIndex = Math.min(fromIndex + resolvedSize, filtered.size());
        List<CandidateApplicationResponseDto> items = filtered.subList(fromIndex, toIndex).stream().map(this::toApplicationResponse).toList();
        return PagedResultDto.<CandidateApplicationResponseDto>builder()
                .items(items)
                .page(resolvedPage)
                .size(resolvedSize)
                .totalElements(filtered.size())
                .totalPages((int) Math.ceil(filtered.size() / (double) resolvedSize))
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public RecruitmentPipelineResponseDto getPipeline(Long jobPositionId) {
        requireViewPermission();
        List<CandidateApplication> applications = jobPositionId == null
                ? candidateApplicationRepository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
                : candidateApplicationRepository.findByJobPositionIdOrderByAppliedAtDesc(jobPositionId);
        List<RecruitmentPipelineColumnDto> columns = new ArrayList<>();
        for (CandidatePipelineStatus stage : CandidatePipelineStatus.values()) {
            List<CandidateApplicationResponseDto> stageItems = applications.stream()
                    .filter(application -> application.getStatus() == stage)
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
        Employee interviewer = getEmployeeOrThrow(requestDto.getInterviewerEmployeeId());
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
        auditLogService.logAction(AuditActionType.SCHEDULE, AuditEntityType.INTERVIEW, saved.getId(), jsonField("applicationId", String.valueOf(application.getId())));
        emailNotificationService.sendInterviewScheduledEmail(
            application.getCandidate().getEmail(),
            application.getCandidate().getFullName(),
            application.getCandidate().getFullName(),
            application.getJobPosition().getTitle(),
            saved.getScheduledAt(),
            saved.getMode().name(),
            interviewer.getFirstName() + " " + interviewer.getLastName()
        );
        return toInterviewResponse(saved);
    }

    @Override
    public InterviewResponseDto updateInterview(Long interviewId, InterviewScheduleRequestDto requestDto) {
        requireSchedulePermission();
        Interview interview = getInterviewOrThrow(interviewId);
        Employee interviewer = getEmployeeOrThrow(requestDto.getInterviewerEmployeeId());
        interview.setInterviewer(interviewer);
        interview.setMode(requestDto.getMode());
        interview.setScheduledAt(requestDto.getScheduledAt());
        interview.setLocation(trimToNull(requestDto.getLocation()));
        interview.setMeetingLink(trimToNull(requestDto.getMeetingLink()));
        interview.setNotes(trimToNull(requestDto.getNotes()));
        interview.setStatus(InterviewStatus.RESCHEDULED);
        Interview updated = interviewRepository.save(interview);
        auditLogService.logAction(AuditActionType.UPDATE, AuditEntityType.INTERVIEW, updated.getId(), jsonField("scheduledAt", updated.getScheduledAt().toString()));
        emailNotificationService.sendInterviewScheduledEmail(
            interview.getApplication().getCandidate().getEmail(),
            interview.getApplication().getCandidate().getFullName(),
            interview.getApplication().getCandidate().getFullName(),
            interview.getApplication().getJobPosition().getTitle(),
            updated.getScheduledAt(),
            updated.getMode().name(),
            interviewer.getFirstName() + " " + interviewer.getLastName()
        );
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
        return interviewRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(start, end).stream().map(this::toInterviewResponse).toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public RecruitmentDashboardDto getDashboard() {
        requireViewPermission();
        List<CandidateApplication> allApplications = candidateApplicationRepository.findAll();
        long openJobs = jobPositionRepository.countByStatus(JobPositionStatus.OPEN);
        List<RecruitmentStageCountDto> stageCounts = new ArrayList<>();
        for (CandidatePipelineStatus stage : CandidatePipelineStatus.values()) {
            stageCounts.add(RecruitmentStageCountDto.builder().stage(stage).count(candidateApplicationRepository.countByStatus(stage)).build());
        }
        List<RecruitmentJobCountDto> jobCounts = jobPositionRepository.findAll().stream()
                .map(position -> RecruitmentJobCountDto.builder().jobPositionId(position.getId()).title(position.getTitle()).count(candidateApplicationRepository.countByJobPositionId(position.getId())).build())
                .toList();
        long hiredCandidates = stageCounts.stream().filter(item -> item.getStage() == CandidatePipelineStatus.HIRED).mapToLong(RecruitmentStageCountDto::getCount).sum();
        long upcomingInterviews = interviewRepository.findByScheduledAtBetweenOrderByScheduledAtAsc(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(14)).size();
        return RecruitmentDashboardDto.builder()
                .openJobs(openJobs)
                .totalCandidates(candidateRepository.count())
                .activeApplications(allApplications.size())
                .hiredCandidates(hiredCandidates)
                .upcomingInterviews(upcomingInterviews)
                .stageCounts(stageCounts)
                .jobCounts(jobCounts)
                .build();
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
        return jobPositionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job position not found with id: " + id));
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

    private void applyJobPosition(JobPosition position, String title, String department, String description, EmploymentType employmentType, String location, Integer openings, JobPositionStatus status, Boolean published) {
        position.setTitle(title.trim());
        position.setDepartment(trimToNull(department));
        position.setDescription(trimToNull(description));
        position.setEmploymentType(employmentType);
        position.setLocation(trimToNull(location));
        position.setOpenings(openings);
        position.setStatus(status == null ? JobPositionStatus.OPEN : status);
        position.setPublished(Boolean.TRUE.equals(published));
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
        if (application.getStatus() == CandidatePipelineStatus.HIRED) {
            throw new BadRequestException("Hired applications are locked after employee creation");
        }
        if (requestDto.getStatus() == CandidatePipelineStatus.HIRED) {
            throw new BadRequestException("Use the hire workflow to create the employee account and mark this application as hired");
        }
        application.setStatus(requestDto.getStatus());
        application.setRecruiterNotes(trimToNull(requestDto.getRecruiterNotes()));
        application.setRejectedReason(trimToNull(requestDto.getRejectedReason()));
        application.setExpectedSalary(requestDto.getExpectedSalary());
        if (requestDto.getStatus() == CandidatePipelineStatus.OFFERED) {
            application.setOfferedAt(LocalDateTime.now());
        }
        if (requestDto.getStatus() == CandidatePipelineStatus.HIRED) {
            application.setHiredAt(LocalDateTime.now());
        }
    }

    private void validateApplicationCanBeHired(CandidateApplication application) {
        if (application.getStatus() == CandidatePipelineStatus.HIRED || application.getHiredAt() != null) {
            throw new BadRequestException("Candidate application is already hired");
        }
        if (application.getStatus() == CandidatePipelineStatus.REJECTED) {
            throw new BadRequestException("Rejected applications cannot be hired without first reopening the recruitment decision");
        }
        if (!canHireFromStatus(application.getStatus())) {
            throw new BadRequestException("Application must reach interview, technical, HR review, or offer stage before hiring");
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

    private boolean canHireFromStatus(CandidatePipelineStatus status) {
        return status == CandidatePipelineStatus.INTERVIEW
                || status == CandidatePipelineStatus.TECHNICAL
                || status == CandidatePipelineStatus.HR_REVIEW
                || status == CandidatePipelineStatus.OFFERED;
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
        if (!resolvedRole.isTenantBusinessRole() || resolvedRole == PlatformRole.PLATFORM_ADMIN) {
            throw new BadRequestException("Only tenant business roles can be assigned when hiring a candidate");
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

    private void notifyCandidateStatus(CandidateApplication application) {
        try {
            String updatedBy = authorizationService.getCurrentEmployeeOrNull() == null
                    ? "WorkNest"
                    : authorizationService.getCurrentEmployeeOrNull().getFirstName() + " " + authorizationService.getCurrentEmployeeOrNull().getLastName();
            emailNotificationService.sendCandidateStatusUpdateEmail(
                    application.getCandidate().getEmail(),
                    application.getCandidate().getFullName(),
                    application.getCandidate().getFullName(),
                    application.getJobPosition().getTitle(),
                    application.getStatus().name(),
                    updatedBy
            );
        } catch (Exception ignored) {
        }
    }

    private CandidateApplicationResponseDto toApplicationResponse(CandidateApplication application) {
        return CandidateApplicationResponseDto.builder()
                .id(application.getId())
                .candidate(toCandidateResponse(application.getCandidate()))
                .jobPosition(toJobPositionResponse(application.getJobPosition()))
                .status(application.getStatus())
                .coverLetter(application.getCoverLetter())
                .expectedSalary(application.getExpectedSalary())
                .recruiterNotes(application.getRecruiterNotes())
                .rejectedReason(application.getRejectedReason())
                .createdBy(tenantDtoMapper.toEmployeeSimple(application.getCreatedBy()))
                .appliedAt(application.getAppliedAt())
                .updatedAt(application.getUpdatedAt())
                .offeredAt(application.getOfferedAt())
                .hiredAt(application.getHiredAt())
                .build();
    }

    private InterviewResponseDto toInterviewResponse(Interview interview) {
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
                .feedback(interviewFeedbackRepository.findByInterviewId(interview.getId()).map(this::toInterviewFeedbackResponse).orElse(null))
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
                .currentTitle(candidate.getCurrentTitle())
                .yearsOfExperience(candidate.getYearsOfExperience())
                .source(candidate.getSource())
                .summary(candidate.getSummary())
                .resumeFileName(candidate.getResumeFileName())
                .resumeFileUrl(candidate.getResumeFileUrl())
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
                .department(position.getDepartment())
                .description(position.getDescription())
                .employmentType(position.getEmploymentType())
                .location(position.getLocation())
                .openings(position.getOpenings())
                .status(position.getStatus())
                .published(position.isPublished())
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

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
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
        return name.toLowerCase().replace('_', ' ');
    }
}
