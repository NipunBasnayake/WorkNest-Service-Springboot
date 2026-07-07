package com.worknest.tenant.service;

import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.recruitment.*;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

public interface RecruitmentService {

    JobPositionResponseDto createJobPosition(JobPositionCreateRequestDto requestDto);
    JobPositionResponseDto updateJobPosition(Long jobPositionId, JobPositionUpdateRequestDto requestDto);
    JobPositionResponseDto getJobPositionById(Long jobPositionId);
    PagedResultDto<JobPositionResponseDto> listJobPositions(String search, int page, int size, String sortBy, String sortDir);
    void deleteJobPosition(Long jobPositionId);

    CandidateResponseDto createCandidate(CandidateCreateRequestDto requestDto);
    CandidateResponseDto updateCandidate(Long candidateId, CandidateUpdateRequestDto requestDto);
    CandidateResponseDto getCandidateById(Long candidateId);
    PagedResultDto<CandidateResponseDto> listCandidates(String search, int page, int size, String sortBy, String sortDir);
    CandidateResponseDto uploadCandidateResume(Long candidateId, MultipartFile resumeFile);
    CandidateCommentResponseDto addCandidateComment(CandidateCommentCreateRequestDto requestDto);
    List<CandidateCommentResponseDto> listCandidateComments(Long candidateId);

    CandidateApplicationResponseDto createApplication(CandidateApplicationCreateRequestDto requestDto);
    CandidateApplicationResponseDto updateApplication(Long applicationId, CandidateApplicationUpdateRequestDto requestDto);
    CandidateApplicationResponseDto updateApplicationStatus(Long applicationId, CandidateApplicationUpdateRequestDto requestDto);
    RecruitmentHireResponseDto hireApplication(Long applicationId, RecruitmentHireRequestDto requestDto);
    CandidateApplicationResponseDto getApplicationById(Long applicationId);
    PagedResultDto<CandidateApplicationResponseDto> listApplications(String search, CandidatePipelineStatus status, Long jobPositionId, int page, int size, String sortBy, String sortDir);
    RecruitmentPipelineResponseDto getPipeline(Long jobPositionId);

    InterviewResponseDto scheduleInterview(InterviewScheduleRequestDto requestDto);
    InterviewResponseDto updateInterview(Long interviewId, InterviewScheduleRequestDto requestDto);
    InterviewFeedbackResponseDto submitFeedback(InterviewFeedbackRequestDto requestDto);
    List<InterviewResponseDto> listUpcomingInterviews(LocalDateTime from, LocalDateTime to);

    RecruitmentDashboardDto getDashboard();
}
