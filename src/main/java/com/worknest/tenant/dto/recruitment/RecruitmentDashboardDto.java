package com.worknest.tenant.dto.recruitment;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class RecruitmentDashboardDto {
    private long openJobs;
    private long totalCandidates;
    private long activeApplications;
    private long hiredCandidates;
    private long upcomingInterviews;
    private List<RecruitmentStageCountDto> stageCounts;
    private List<RecruitmentJobCountDto> jobCounts;
}