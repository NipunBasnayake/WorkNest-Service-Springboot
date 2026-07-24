package com.worknest.tenant.service.impl;

import com.worknest.security.authorization.AuthorizationService;
import com.worknest.tenant.dto.report.RecruitmentReportPageDto;
import com.worknest.tenant.entity.Candidate;
import com.worknest.tenant.entity.CandidateApplication;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Interview;
import com.worknest.tenant.entity.InterviewFeedback;
import com.worknest.tenant.entity.JobPosition;
import com.worknest.tenant.enums.InterviewMode;
import com.worknest.tenant.enums.InterviewRecommendation;
import com.worknest.tenant.enums.InterviewStatus;
import com.worknest.tenant.enums.RecruitmentReportType;
import com.worknest.tenant.repository.CandidateApplicationRepository;
import com.worknest.tenant.repository.InterviewFeedbackRepository;
import com.worknest.tenant.repository.InterviewRepository;
import com.worknest.tenant.repository.JobPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentReportServiceImplTest {

    @Mock
    private JobPositionRepository jobPositionRepository;
    @Mock
    private CandidateApplicationRepository candidateApplicationRepository;
    @Mock
    private InterviewRepository interviewRepository;
    @Mock
    private InterviewFeedbackRepository interviewFeedbackRepository;
    @Mock
    private AuthorizationService authorizationService;

    private RecruitmentReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new RecruitmentReportServiceImpl(
                jobPositionRepository,
                candidateApplicationRepository,
                interviewRepository,
                interviewFeedbackRepository,
                authorizationService);
    }

    @Test
    void keepsInterviewStatusesExactAndUsesFeedbackForOutcomes() {
        List<Interview> interviews = List.of(
                interview(1L, InterviewStatus.SCHEDULED, InterviewMode.REMOTE, LocalDateTime.of(2026, 7, 1, 9, 0)),
                interview(2L, InterviewStatus.RESCHEDULED, InterviewMode.ONSITE, LocalDateTime.of(2026, 7, 2, 10, 0)),
                interview(3L, InterviewStatus.COMPLETED, InterviewMode.PHONE, LocalDateTime.of(2026, 7, 3, 11, 0)),
                interview(4L, InterviewStatus.CANCELLED, InterviewMode.REMOTE, LocalDateTime.of(2026, 7, 4, 12, 0)));
        List<InterviewFeedback> feedback = List.of(
                feedback(interviews.get(2), InterviewRecommendation.HIRE),
                feedback(interviews.get(3), InterviewRecommendation.NO_HIRE));
        when(interviewRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(interviews));
        when(interviewRepository.findAll(any(Specification.class))).thenReturn(interviews);
        when(interviewRepository.count(any(Specification.class))).thenReturn(1L);
        when(interviewFeedbackRepository.findByInterviewIdIn(any())).thenReturn(feedback);

        RecruitmentReportPageDto report = reportService.getReport(
                RecruitmentReportType.INTERVIEWS,
                null,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                null,
                Map.of(),
                0,
                20,
                null,
                "desc");

        Map<String, Object> statusChart = chart(report, "Interview Status");
        assertThat(labels(statusChart)).containsExactly("SCHEDULED", "COMPLETED", "CANCELLED", "RESCHEDULED");
        assertThat(chartTotal(statusChart)).isEqualTo(interviews.size());
        assertThat(labels(chart(report, "Interview Outcome"))).containsExactly("HIRE", "NO_HIRE");
        assertThat(report.getSummary()).containsEntry("Recorded outcomes", 2L)
                .containsEntry("Successful outcomes", 1L);
        assertThat(report.getRows()).extracting(row -> row.get("status"))
                .containsExactly("SCHEDULED", "RESCHEDULED", "COMPLETED", "CANCELLED");
    }

    private Map<String, Object> chart(RecruitmentReportPageDto report, String title) {
        return report.getSupportingCharts().stream()
                .filter(chart -> title.equals(chart.get("title")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<String> labels(Map<String, Object> chart) {
        return ((List<Map<String, Object>>) chart.get("data")).stream()
                .map(point -> String.valueOf(point.get("label")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private long chartTotal(Map<String, Object> chart) {
        return ((List<Map<String, Object>>) chart.get("data")).stream()
                .mapToLong(point -> ((Number) point.get("value")).longValue())
                .sum();
    }

    private Interview interview(Long id, InterviewStatus status, InterviewMode mode, LocalDateTime scheduledAt) {
        Candidate candidate = new Candidate();
        candidate.setFullName("Candidate " + id);
        JobPosition jobPosition = new JobPosition();
        jobPosition.setTitle("Engineer");
        jobPosition.setDepartment("Engineering");
        CandidateApplication application = new CandidateApplication();
        application.setCandidate(candidate);
        application.setJobPosition(jobPosition);
        Employee interviewer = new Employee();
        interviewer.setId(20L);
        interviewer.setFirstName("Alex");
        interviewer.setLastName("Morgan");

        Interview interview = new Interview();
        interview.setId(id);
        interview.setApplication(application);
        interview.setInterviewer(interviewer);
        interview.setStatus(status);
        interview.setMode(mode);
        interview.setScheduledAt(scheduledAt);
        return interview;
    }

    private InterviewFeedback feedback(Interview interview, InterviewRecommendation recommendation) {
        InterviewFeedback feedback = new InterviewFeedback();
        feedback.setInterview(interview);
        feedback.setRecommendation(recommendation);
        return feedback;
    }
}
