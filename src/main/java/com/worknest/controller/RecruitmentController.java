package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.recruitment.*;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import com.worknest.tenant.service.RecruitmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/{tenantSlug}/recruitment")
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    public RecruitmentController(RecruitmentService recruitmentService) {
        this.recruitmentService = recruitmentService;
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> createJob(@Valid @RequestBody JobPositionCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Job position created successfully", recruitmentService.createJobPosition(requestDto)));
    }

    @PutMapping("/jobs/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> updateJob(@PathVariable("id") @Positive Long id, @Valid @RequestBody JobPositionUpdateRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Job position updated successfully", recruitmentService.updateJobPosition(id, requestDto)));
    }

    @PostMapping("/jobs/{id:\\d+}/publish")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> publishJob(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Job opening published successfully", recruitmentService.publishJobPosition(id)));
    }

    @PostMapping("/jobs/{id:\\d+}/unpublish")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> unpublishJob(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Job opening unpublished successfully", recruitmentService.unpublishJobPosition(id)));
    }

    @PostMapping("/jobs/{id:\\d+}/close")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> closeJob(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Job opening closed successfully", recruitmentService.closeJobPosition(id)));
    }

    @PostMapping("/jobs/{id:\\d+}/reopen")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> reopenJob(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Job opening reopened successfully", recruitmentService.reopenJobPosition(id)));
    }

    @PostMapping("/jobs/{id:\\d+}/duplicate")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> duplicateJob(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Job opening duplicated as a draft", recruitmentService.duplicateJobPosition(id)));
    }

    @GetMapping("/jobs/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> getJob(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Job position retrieved successfully", recruitmentService.getJobPositionById(id)));
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<PagedResultDto<JobPositionResponseDto>>> listJobs(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.success("Job positions retrieved successfully", recruitmentService.listJobPositions(search, page, size, sortBy, sortDir)));
    }

    @DeleteMapping("/jobs/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable("id") @Positive Long id) {
        recruitmentService.deleteJobPosition(id);
        return ResponseEntity.ok(ApiResponse.success("Job position deleted successfully"));
    }

    @PostMapping("/candidates")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateResponseDto>> createCandidate(@Valid @RequestBody CandidateCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Candidate created successfully", recruitmentService.createCandidate(requestDto)));
    }

    @PutMapping("/candidates/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateResponseDto>> updateCandidate(@PathVariable("id") @Positive Long id, @Valid @RequestBody CandidateUpdateRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Candidate updated successfully", recruitmentService.updateCandidate(id, requestDto)));
    }

    @GetMapping("/candidates/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateResponseDto>> getCandidate(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Candidate retrieved successfully", recruitmentService.getCandidateById(id)));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<PagedResultDto<CandidateResponseDto>>> listCandidates(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.success("Candidates retrieved successfully", recruitmentService.listCandidates(search, page, size, sortBy, sortDir)));
    }

    @PostMapping(value = "/candidates/{id:\\d+}/resume", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateResponseDto>> uploadResume(@PathVariable("id") @Positive Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Resume uploaded successfully", recruitmentService.uploadCandidateResume(id, file)));
    }

    @PostMapping("/candidates/comments")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateCommentResponseDto>> addComment(@Valid @RequestBody CandidateCommentCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Candidate comment added successfully", recruitmentService.addCandidateComment(requestDto)));
    }

    @GetMapping("/candidates/{id:\\d+}/comments")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<CandidateCommentResponseDto>>> listComments(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Candidate comments retrieved successfully", recruitmentService.listCandidateComments(id)));
    }

    @PostMapping("/applications")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateApplicationResponseDto>> createApplication(@Valid @RequestBody CandidateApplicationCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Application created successfully", recruitmentService.createApplication(requestDto)));
    }

    @PatchMapping("/applications/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateApplicationResponseDto>> updateApplication(@PathVariable("id") @Positive Long id, @Valid @RequestBody CandidateApplicationUpdateRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Application updated successfully", recruitmentService.updateApplication(id, requestDto)));
    }

    @PatchMapping("/applications/{id:\\d+}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateApplicationResponseDto>> updateApplicationStatus(@PathVariable("id") @Positive Long id, @Valid @RequestBody CandidateApplicationUpdateRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Application status updated successfully", recruitmentService.updateApplicationStatus(id, requestDto)));
    }

    @PostMapping("/applications/{id:\\d+}/hire")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RecruitmentHireResponseDto>> hireApplication(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody RecruitmentHireRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Candidate hired and employee account created successfully", recruitmentService.hireApplication(id, requestDto)));
    }

    @GetMapping("/applications/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateApplicationResponseDto>> getApplication(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Application retrieved successfully", recruitmentService.getApplicationById(id)));
    }

    @PostMapping("/applications/{id:\\d+}/notes")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<CandidateCommentResponseDto>> addApplicationNote(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody RecruitmentApplicationNoteRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Application note added successfully", recruitmentService.addApplicationNote(id, requestDto)));
    }

    @GetMapping("/applications/{id:\\d+}/notes")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<CandidateCommentResponseDto>>> listApplicationNotes(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Application notes retrieved successfully", recruitmentService.listApplicationNotes(id)));
    }

    @GetMapping("/applications/{id:\\d+}/timeline")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<RecruitmentApplicationEventResponseDto>>> listApplicationTimeline(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Application timeline retrieved successfully", recruitmentService.listApplicationTimeline(id)));
    }

    @PostMapping("/applications/{id:\\d+}/emails")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RecruitmentEmailLogResponseDto>> sendApplicationEmail(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody RecruitmentSendEmailRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Candidate email queued successfully", recruitmentService.sendApplicationEmail(id, requestDto)));
    }

    @GetMapping("/applications/{id:\\d+}/emails")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<RecruitmentEmailLogResponseDto>>> listApplicationEmails(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Candidate emails retrieved successfully", recruitmentService.listApplicationEmails(id)));
    }

    @GetMapping("/applications/{id:\\d+}/interviews")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<InterviewResponseDto>>> listApplicationInterviews(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Application interviews retrieved successfully", recruitmentService.listApplicationInterviews(id)));
    }

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<PagedResultDto<CandidateApplicationResponseDto>>> listApplications(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) CandidatePipelineStatus status,
            @RequestParam(value = "jobPositionId", required = false) Long jobPositionId,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "appliedAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.success("Applications retrieved successfully", recruitmentService.listApplications(search, status, jobPositionId, page, size, sortBy, sortDir)));
    }

    @GetMapping("/pipeline")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RecruitmentPipelineResponseDto>> getPipeline(@RequestParam(value = "jobPositionId", required = false) Long jobPositionId) {
        return ResponseEntity.ok(ApiResponse.success("Pipeline retrieved successfully", recruitmentService.getPipeline(jobPositionId)));
    }

    @PostMapping("/interviews")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<InterviewResponseDto>> scheduleInterview(@Valid @RequestBody InterviewScheduleRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Interview scheduled successfully", recruitmentService.scheduleInterview(requestDto)));
    }

    @PutMapping("/interviews/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<InterviewResponseDto>> updateInterview(@PathVariable("id") @Positive Long id, @Valid @RequestBody InterviewScheduleRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Interview updated successfully", recruitmentService.updateInterview(id, requestDto)));
    }

    @PostMapping("/interviews/feedback")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<InterviewFeedbackResponseDto>> submitFeedback(@Valid @RequestBody InterviewFeedbackRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Interview feedback saved successfully", recruitmentService.submitFeedback(requestDto)));
    }

    @GetMapping("/interviews")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<InterviewResponseDto>>> listInterviews(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success("Interviews retrieved successfully", recruitmentService.listUpcomingInterviews(from, to)));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RecruitmentDashboardDto>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Recruitment dashboard retrieved successfully", recruitmentService.getDashboard()));
    }

    @GetMapping("/email-templates")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<RecruitmentEmailTemplateResponseDto>>> listEmailTemplates() {
        return ResponseEntity.ok(ApiResponse.success("Recruitment email templates retrieved successfully", recruitmentService.listEmailTemplates()));
    }

    @PutMapping("/email-templates/{type}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<RecruitmentEmailTemplateResponseDto>> updateEmailTemplate(
            @PathVariable("type") com.worknest.tenant.enums.RecruitmentEmailTemplateType type,
            @Valid @RequestBody RecruitmentEmailTemplateUpdateRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Recruitment email template updated successfully", recruitmentService.updateEmailTemplate(type, requestDto)));
    }
}
