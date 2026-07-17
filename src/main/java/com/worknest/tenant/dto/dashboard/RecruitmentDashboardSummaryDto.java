package com.worknest.tenant.dto.dashboard;

import com.worknest.tenant.enums.CandidatePipelineStatus;
import com.worknest.tenant.enums.InterviewMode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class RecruitmentDashboardSummaryDto {
    private long openJobs;
    private long applicationsReceived;
    private long shortlisted;
    private long interviewsScheduled;
    private long offers;
    private long hired;
    private List<RecentApplicationDto> recentApplications;
    private List<UpcomingInterviewDto> upcomingInterviews;

    @Getter
    @Builder
    public static class RecentApplicationDto {
        private Long id;
        private String candidateName;
        private String jobTitle;
        private CandidatePipelineStatus status;
        private LocalDateTime appliedAt;
    }

    @Getter
    @Builder
    public static class UpcomingInterviewDto {
        private Long id;
        private Long applicationId;
        private String candidateName;
        private String jobTitle;
        private InterviewMode mode;
        private LocalDateTime scheduledAt;
    }
}
