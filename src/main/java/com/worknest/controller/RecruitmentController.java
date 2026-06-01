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
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> createJob(@Valid @RequestBody JobPositionCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Job position created successfully", recruitmentService.createJobPosition(requestDto)));
    }

    @PutMapping("/jobs/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> updateJob(@PathVariable("id") @Positive Long id, @Valid @RequestBody JobPositionUpdateRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Job position updated successfully", recruitmentService.updateJobPosition(id, requestDto)));
    }

    @GetMapping("/jobs/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<JobPositionResponseDto>> getJob(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Job position retrieved successfully", recruitmentService.getJobPositionById(id)));
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<JobPositionResponseDto>>> listJobs(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.success("Job positions retrieved successfully", recruitmentService.listJobPositions(search, page, size, sortBy, sortDir)));
    }

    @DeleteMapping("/jobs/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable("id") @Positive Long id) {
        recruitmentService.deleteJobPosition(id);
        return ResponseEntity.ok(ApiResponse.success("Job position deleted successfully"));
    }

    @PostMapping("/candidates")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<CandidateResponseDto>> createCandidate(@Valid @RequestBody CandidateCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Candidate created successfully", recruitmentService.createCandidate(requestDto)));
    }

    @PutMapping("/candidates/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<CandidateResponseDto>> updateCandidate(@PathVariable("id") @Positive Long id, @Valid @RequestBody CandidateUpdateRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Candidate updated successfully", recruitmentService.updateCandidate(id, requestDto)));
    }

    @GetMapping("/candidates/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<CandidateResponseDto>> getCandidate(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Candidate retrieved successfully", recruitmentService.getCandidateById(id)));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<CandidateResponseDto>>> listCandidates(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.success("Candidates retrieved successfully", recruitmentService.listCandidates(search, page, size, sortBy, sortDir)));
    }

    @PostMapping(value = "/candidates/{id:\\d+}/resume", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<CandidateResponseDto>> uploadResume(@PathVariable("id") @Positive Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Resume uploaded successfully", recruitmentService.uploadCandidateResume(id, file)));
    }

    @PostMapping("/candidates/comments")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<CandidateCommentResponseDto>> addComment(@Valid @RequestBody CandidateCommentCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Candidate comment added successfully", recruitmentService.addCandidateComment(requestDto)));
    }

    @GetMapping("/candidates/{id:\\d+}/comments")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<CandidateCommentResponseDto>>> listComments(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Candidate comments retrieved successfully", recruitmentService.listCandidateComments(id)));
    }

    @PostMapping("/applications")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<CandidateApplicationResponseDto>> createApplication(@Valid @RequestBody CandidateApplicationCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Application created successfully", recruitmentService.createApplication(requestDto)));
    }

    @PatchMapping("/applications/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<CandidateApplicationResponseDto>> updateApplication(@PathVariable("id") @Positive Long id, @Valid @RequestBody CandidateApplicationUpdateRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Application updated successfully", recruitmentService.updateApplication(id, requestDto)));
    }

    @PatchMapping("/applications/{id:\\d+}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<CandidateApplicationResponseDto>> updateApplicationStatus(@PathVariable("id") @Positive Long id, @Valid @RequestBody CandidateApplicationUpdateRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Application status updated successfully", recruitmentService.updateApplicationStatus(id, requestDto)));
    }

    @GetMapping("/applications/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<CandidateApplicationResponseDto>> getApplication(@PathVariable("id") @Positive Long id) {
        return ResponseEntity.ok(ApiResponse.success("Application retrieved successfully", recruitmentService.getApplicationById(id)));
    }

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
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
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<RecruitmentPipelineResponseDto>> getPipeline(@RequestParam(value = "jobPositionId", required = false) Long jobPositionId) {
        return ResponseEntity.ok(ApiResponse.success("Pipeline retrieved successfully", recruitmentService.getPipeline(jobPositionId)));
    }

    @PostMapping("/interviews")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<InterviewResponseDto>> scheduleInterview(@Valid @RequestBody InterviewScheduleRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Interview scheduled successfully", recruitmentService.scheduleInterview(requestDto)));
    }

    @PutMapping("/interviews/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<InterviewResponseDto>> updateInterview(@PathVariable("id") @Positive Long id, @Valid @RequestBody InterviewScheduleRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Interview updated successfully", recruitmentService.updateInterview(id, requestDto)));
    }

    @PostMapping("/interviews/feedback")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<InterviewFeedbackResponseDto>> submitFeedback(@Valid @RequestBody InterviewFeedbackRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Interview feedback saved successfully", recruitmentService.submitFeedback(requestDto)));
    }

    @GetMapping("/interviews")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<InterviewResponseDto>>> listInterviews(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(ApiResponse.success("Interviews retrieved successfully", recruitmentService.listUpcomingInterviews(from, to)));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<RecruitmentDashboardDto>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Recruitment dashboard retrieved successfully", recruitmentService.getDashboard()));
    }
}