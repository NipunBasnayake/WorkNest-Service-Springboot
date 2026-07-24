package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.analytics.*;
import com.worknest.tenant.dto.dashboard.ProjectTaskProgressDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Project;
import com.worknest.tenant.entity.ProjectTeam;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.enums.AttendanceStatus;
import com.worknest.tenant.enums.LeaveStatus;
import com.worknest.tenant.enums.LeaveType;
import com.worknest.tenant.enums.ProjectStatus;
import com.worknest.tenant.enums.CandidatePipelineStatus;
import com.worknest.tenant.enums.JobPositionStatus;
import com.worknest.tenant.enums.TaskStatus;
import com.worknest.tenant.repository.*;
import com.worknest.tenant.service.AnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(transactionManager = "transactionManager", readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TaskRepository taskRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final JobPositionRepository jobPositionRepository;
    private final CandidateApplicationRepository candidateApplicationRepository;
    private final InterviewRepository interviewRepository;
    private final NotificationRepository notificationRepository;
    private final AnnouncementRepository announcementRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityUtils securityUtils;
    private final TenantDtoMapper tenantDtoMapper;

    public AnalyticsServiceImpl(
            EmployeeRepository employeeRepository,
            TeamRepository teamRepository,
            ProjectRepository projectRepository,
            ProjectTeamRepository projectTeamRepository,
            TaskRepository taskRepository,
            LeaveRequestRepository leaveRequestRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            TeamMemberRepository teamMemberRepository,
            JobPositionRepository jobPositionRepository,
            CandidateApplicationRepository candidateApplicationRepository,
            InterviewRepository interviewRepository,
            NotificationRepository notificationRepository,
            AnnouncementRepository announcementRepository,
            AuditLogRepository auditLogRepository,
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper) {
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.projectRepository = projectRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.taskRepository = taskRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.candidateApplicationRepository = candidateApplicationRepository;
        this.interviewRepository = interviewRepository;
        this.notificationRepository = notificationRepository;
        this.announcementRepository = announcementRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
    }

    @Override
    @Cacheable(cacheNames = "businessIntelligence", key = "#tenantSlug + ':' + T(java.util.Objects).hash(#fromDate,#toDate,#department,#teamId,#employeeId,#projectId,#taskStatus,#recruitmentStatus,#attendancePeriod,#leaveType)")
    public BusinessIntelligenceReportDto getBusinessIntelligenceReport(
            String tenantSlug, LocalDate fromDate, LocalDate toDate, String department,
            Long teamId, Long employeeId, Long projectId, String taskStatus,
            String recruitmentStatus, String attendancePeriod, String leaveType) {
        LocalDate end = toDate == null ? LocalDate.now() : toDate;
        LocalDate start = fromDate == null ? end.minusDays(29) : fromDate;
        if (end.isBefore(start)) throw new BadRequestException("toDate cannot be before fromDate");
        if (start.plusYears(2).isBefore(end)) throw new BadRequestException("Date range cannot exceed two years");
        String normalizedDepartment = blankToNull(department);
        TaskStatus selectedTaskStatus = parseEnum(TaskStatus.class, taskStatus, "taskStatus");
        CandidatePipelineStatus selectedRecruitmentStatus = parseEnum(CandidatePipelineStatus.class, recruitmentStatus, "recruitmentStatus");
        LeaveType selectedLeaveType = parseEnum(LeaveType.class, leaveType, "leaveType");
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay().minusNanos(1);

        List<Object[]> employeeStatus = employeeRepository.countStatusForReport(start, end, normalizedDepartment, employeeId, teamId, projectId);
        List<Object[]> employeeJoining = employeeRepository.countJoiningTrendForReport(start, end, normalizedDepartment, employeeId, teamId, projectId);
        long rangeDays = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(rangeDays - 1);
        double currentJoins = sum(employeeJoining);
        double previousJoins = sum(employeeRepository.countJoiningTrendForReport(previousStart, previousEnd, normalizedDepartment, employeeId, teamId, projectId));
        Double employeeGrowth = previousJoins == 0 ? null : round((currentJoins - previousJoins) * 100d / previousJoins);

        List<Object[]> projectStatus = projectRepository.countStatusForReport(projectId, teamId, employeeId, startTime, endTime);
        List<Object[]> taskStatusRows = taskRepository.countStatusForReport(startTime, endTime, projectId, teamId, employeeId, selectedTaskStatus);
        List<Object[]> taskPriorityRows = taskRepository.countPriorityForReport(startTime, endTime, projectId, teamId, employeeId, selectedTaskStatus);
        List<Object[]> dailyAttendanceRows = attendanceRecordRepository.summarizeForReport(start, end, employeeId, normalizedDepartment);
        String attendanceGrouping = blankToNull(attendancePeriod) == null ? "daily" : attendancePeriod.toLowerCase(Locale.ROOT);
        List<Object[]> attendanceRows = switch (attendanceGrouping) {
            case "weekly" -> attendanceRecordRepository.summarizeWeeklyForReport(start, end, employeeId, normalizedDepartment);
            case "monthly" -> attendanceRecordRepository.summarizeMonthlyForReport(start, end, employeeId, normalizedDepartment);
            case "daily" -> dailyAttendanceRows;
            default -> throw new BadRequestException("attendancePeriod has an unsupported value");
        };
        List<Object[]> leaveStatusRows = leaveRequestRepository.countStatusForReport(start, end, employeeId, normalizedDepartment, selectedLeaveType);
        List<Object[]> recruitmentRows = canonicalRecruitmentRows(
                candidateApplicationRepository.countPipelineForReport(startTime, endTime, selectedRecruitmentStatus, normalizedDepartment));
        List<Object[]> teamSizes = teamRepository.countSizesForReport(teamId, employeeId, normalizedDepartment, projectId);
        if (employeeId != null || normalizedDepartment != null) {
            teamSizes = teamSizes.stream().filter(row -> number(row[2]) > 0).toList();
        }

        Map<String, Double> employees = rowMap(employeeStatus);
        Map<String, Double> projects = rowMap(projectStatus);
        Map<String, Double> tasks = rowMap(taskStatusRows);
        Map<String, Double> leaves = rowMap(leaveStatusRows);
        Map<String, Double> recruitment = rowMap(recruitmentRows);
        double totalEmployees = employees.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalTasks = tasks.values().stream().mapToDouble(Double::doubleValue).sum();
        double completedTasks = tasks.getOrDefault(TaskStatus.DONE.name(), 0d);
        double taskCompletion = totalTasks == 0 ? 0 : round(completedTasks * 100d / totalTasks);
        long overdue = taskRepository.countOverdueForReport(LocalDate.now(), startTime, endTime, projectId, teamId, employeeId, selectedTaskStatus);
        Object[] latestAttendance = dailyAttendanceRows.isEmpty() ? null : dailyAttendanceRows.get(dailyAttendanceRows.size() - 1);
        double presentToday = latestAttendance == null ? 0 : number(latestAttendance[1]);
        double lateToday = latestAttendance == null ? 0 : number(latestAttendance[2]);
        double absentToday = latestAttendance == null ? 0 : number(latestAttendance[3]);
        double halfDayToday = latestAttendance == null ? 0 : number(latestAttendance[4]);
        double incompleteToday = latestAttendance == null ? 0 : number(latestAttendance[5]);
        double attendanceTotal = presentToday + lateToday + absentToday + halfDayToday + incompleteToday;
        double attendanceRate = attendanceTotal == 0 ? 0 : round((presentToday + lateToday + halfDayToday) * 100d / attendanceTotal);
        double hired = recruitment.getOrDefault(CandidatePipelineStatus.HIRED.name(), 0d);
        double offered = recruitment.getOrDefault(CandidatePipelineStatus.OFFERED.name(), 0d);
        double applications = recruitment.values().stream().mapToDouble(Double::doubleValue).sum();
        double acceptance = offered + hired == 0 ? 0 : round(hired * 100d / (offered + hired));
        double largestTeam = teamSizes.stream().mapToDouble(row -> number(row[2])).max().orElse(0);
        double averageTeam = teamSizes.stream().mapToDouble(row -> number(row[2])).average().orElse(0);
        double totalProjects = projects.values().stream().mapToDouble(Double::doubleValue).sum();
        double averageLeaveDays = round(leaveRequestRepository.averageLeaveDaysForReport(
                start, end, employeeId, normalizedDepartment, selectedLeaveType == null ? null : selectedLeaveType.name()));
        long openJobs = jobPositionRepository.countByStatusForReport(JobPositionStatus.OPEN, normalizedDepartment);
        long interviewCount = interviewRepository.countForReport(startTime, endTime, normalizedDepartment, selectedRecruitmentStatus);

        List<BusinessIntelligenceReportDto.MetricDto> kpis = List.of(
                metric("employees", "Employees", totalEmployees, "", employeeGrowth, "purple", "People in the selected scope"),
                metric("activeEmployees", "Active employees", employees.getOrDefault(UserStatus.ACTIVE.name(), 0d), "", null, "green", "Available workforce"),
                metric("newEmployees", "New employees", currentJoins, "", employeeGrowth, "blue", "Joined in this period"),
                metric("projects", "Total projects", totalProjects, "", null, "purple", "Projects created in the selected scope"),
                metric("runningProjects", "Running projects", projects.getOrDefault(ProjectStatus.IN_PROGRESS.name(), 0d), "", null, "blue", "Currently in delivery"),
                metric("completedProjects", "Completed projects", projects.getOrDefault(ProjectStatus.COMPLETED.name(), 0d), "", null, "green", "Delivered portfolio"),
                metric("projectHealth", "Project health", taskCompletion, totalTasks == 0 ? "" : "%", null, taskCompletion >= 75 ? "green" : "amber", totalTasks == 0 ? "No data available" : "Completed tasks divided by total scoped tasks"),
                metric("openTasks", "Open tasks", totalTasks - completedTasks, "", null, "blue", "Work still in motion"),
                metric("completedTasks", "Completed tasks", completedTasks, "", null, "green", taskCompletion + "% completion rate"),
                metric("blockedTasks", "Blocked tasks", tasks.getOrDefault(TaskStatus.BLOCKED.name(), 0d), "", null, "amber", "Needs dependency action"),
                metric("overdueTasks", "Overdue tasks", overdue, "", null, overdue > 0 ? "red" : "green", "Past the committed due date"),
                metric("presentToday", "Present on time", presentToday, "", null, "green", attendanceTotal == 0 ? "No data available" : attendanceRate + "% attendance"),
                metric("lateToday", "Late arrivals", lateToday, "", null, "amber", "Latest reporting day"),
                metric("absentToday", "Absent", absentToday, "", null, absentToday > 0 ? "red" : "green", "Latest reporting day"),
                metric("attendanceRate", "Attendance rate", attendanceRate, attendanceTotal == 0 ? "" : "%", null, attendanceRate >= 85 ? "green" : "amber", attendanceTotal == 0 ? "No data available" : "Present, late, and half-day records divided by all attendance records"),
                metric("pendingLeave", "Pending leave", leaves.getOrDefault(LeaveStatus.PENDING.name(), 0d), "", null, "amber", "Awaiting a decision"),
                metric("approvedLeave", "Approved leave", leaves.getOrDefault(LeaveStatus.APPROVED.name(), 0d), "", null, "green", "Selected period"),
                metric("averageLeave", "Average leave", averageLeaveDays, leaves.isEmpty() ? "" : " days", null, "purple", leaves.isEmpty() ? "No data available" : "Calendar days per scoped request"),
                metric("openJobs", "Open jobs", openJobs, "", null, "purple", "Recruitment demand in the selected department"),
                metric("applications", "Applications", applications, "", null, "blue", "Selected period"),
                metric("interviews", "Interviews", interviewCount, "", null, "amber", "Scheduled in the selected recruitment scope"),
                metric("hires", "Hires", hired, "", null, "green", acceptance + "% offer acceptance"),
                metric("acceptanceRate", "Acceptance rate", acceptance, offered + hired == 0 ? "" : "%", null, acceptance >= 60 ? "green" : "amber", offered + hired == 0 ? "No data available" : "Hires divided by candidates currently offered or hired"),
                metric("teams", "Teams", teamSizes.size(), "", null, "purple", "Collaboration units in the selected scope"),
                metric("largestTeam", "Largest team", largestTeam, "", null, "blue", "Active members"),
                metric("averageTeam", "Average team size", round(averageTeam), "", null, "purple", "Across active teams"),
                metric("notifications", "Notifications", notificationRepository.count(), "", null, "blue", "System-wide volume"),
                metric("announcements", "Announcements", announcementRepository.count(), "", null, "purple", "Published updates"),
                metric("unreadNotifications", "Unread notifications", notificationRepository.countByReadFalse(), "", null, "amber", "Requires attention")
        );

        Map<String, List<BusinessIntelligenceReportDto.ChartPointDto>> charts = new LinkedHashMap<>();
        charts.put("employeeGrowth", points(employeeJoining));
        charts.put("employeesByDepartment", points(employeeRepository.countDepartmentForReport(start, end, normalizedDepartment, employeeId, teamId, projectId)));
        charts.put("employeesByRole", orderedPoints(employeeRepository.countRoleForReport(start, end, normalizedDepartment, employeeId, teamId, projectId), List.of("TENANT_ADMIN", "ADMIN", "HR", "MANAGER", "EMPLOYEE")));
        charts.put("employeesByDesignation", points(employeeRepository.countDesignationForReport(start, end, normalizedDepartment, employeeId, teamId, projectId)));
        charts.put("employeeStatus", orderedPoints(employeeStatus, List.of("ACTIVE", "INACTIVE", "SUSPENDED")));
        charts.put("projectsByStatus", orderedPoints(projectStatus, List.of("PLANNED", "IN_PROGRESS", "ON_HOLD", "COMPLETED", "CANCELLED")));
        charts.put("projectsCreated", points(projectRepository.countCreatedTrend(projectId, teamId, employeeId, startTime, endTime)));
        charts.put("projectProgress", progressPoints(taskRepository.summarizeProjectProgressForReport(startTime, endTime, projectId, teamId, employeeId, selectedTaskStatus)));
        charts.put("tasksByStatus", orderedPoints(taskStatusRows, List.of("TODO", "IN_PROGRESS", "IN_REVIEW", "BLOCKED", "DONE")));
        charts.put("taskPriority", points(taskPriorityRows));
        charts.put("taskCompletionTrend", triplePoints(taskRepository.countCompletionTrend(startTime, endTime, projectId, teamId, employeeId, selectedTaskStatus == null ? null : selectedTaskStatus.name())));
        charts.put("teamWorkload", points(taskRepository.countOpenWorkloadByTeam(startTime, endTime, projectId, teamId, employeeId, selectedTaskStatus)));
        charts.put("attendanceTrend", triplePoints(attendanceRows));
        charts.put("leaveTypes", points(leaveRequestRepository.countTypeForReport(start, end, employeeId, normalizedDepartment, selectedLeaveType)));
        charts.put("leaveTrend", triplePoints(leaveRequestRepository.countTrendForReport(start, end, employeeId, normalizedDepartment, selectedLeaveType == null ? null : selectedLeaveType.name())));
        charts.put("recruitmentPipeline", orderedPoints(recruitmentRows, List.of("APPLIED", "SHORTLISTED", "INTERVIEW", "OFFERED", "HIRED", "REJECTED")));
        charts.put("applicationsByJob", points(candidateApplicationRepository.countApplicationsByJobForReport(startTime, endTime, selectedRecruitmentStatus, normalizedDepartment)));
        charts.put("hiringTrend", points(candidateApplicationRepository.countHiringTrend(startTime, endTime, selectedRecruitmentStatus == null ? null : selectedRecruitmentStatus.name(), normalizedDepartment)));
        charts.put("recruitmentSources", points(candidateApplicationRepository.countSourcesForReport(startTime, endTime, selectedRecruitmentStatus, normalizedDepartment)));
        charts.put("teamSizes", teamSizes.stream().map(row -> new BusinessIntelligenceReportDto.ChartPointDto(String.valueOf(row[1]), number(row[2]), null, null, String.valueOf(row[0]))).toList());
        charts.put("notificationVolume", points(notificationRepository.countVolumeForReport(startTime, endTime)));
        charts.put("announcementVolume", points(announcementRepository.countCreatedForReport(startTime, endTime)));

        List<BusinessIntelligenceReportDto.RiskDto> risks = new ArrayList<>();
        if (overdue > 0) risks.add(new BusinessIntelligenceReportDto.RiskDto("overdue", overdue > 10 ? "critical" : "warning", "Overdue delivery commitments", "Review owners and recover the oldest tasks first.", overdue, "/tasks?overdue=true"));
        long blocked = Math.round(tasks.getOrDefault(TaskStatus.BLOCKED.name(), 0d));
        if (blocked > 0) risks.add(new BusinessIntelligenceReportDto.RiskDto("blocked", "warning", "Blocked work is accumulating", "Assign dependency owners and escalation dates.", blocked, "/tasks?status=BLOCKED"));
        if (attendanceRate > 0 && attendanceRate < 85) risks.add(new BusinessIntelligenceReportDto.RiskDto("attendance", "critical", "Attendance below target", "Attendance is below the 85% operating target.", Math.round(100 - attendanceRate), "/attendance"));
        if (largestTeam > Math.max(10, averageTeam * 1.8)) risks.add(new BusinessIntelligenceReportDto.RiskDto("team-load", "warning", "Team capacity imbalance", "The largest team is materially above the company average.", Math.round(largestTeam), "/teams"));
        Map<String, Long> risksBySeverity = new LinkedHashMap<>();
        risks.forEach(risk -> risksBySeverity.merge(risk.severity().toUpperCase(Locale.ROOT), 1L, Long::sum));
        charts.put("riskSeverity", risksBySeverity.entrySet().stream()
                .map(entry -> new BusinessIntelligenceReportDto.ChartPointDto(entry.getKey(), entry.getValue().doubleValue(), null, null, null))
                .toList());

        List<BusinessIntelligenceReportDto.InsightDto> insights = new ArrayList<>();
        insights.add(new BusinessIntelligenceReportDto.InsightDto("delivery", taskCompletion >= 75 ? "positive" : "warning", taskCompletion >= 75 ? "Delivery momentum is healthy" : "Delivery throughput needs attention", taskCompletion + "% of scoped tasks are complete.", "/tasks"));
        if (!teamSizes.isEmpty()) insights.add(new BusinessIntelligenceReportDto.InsightDto("capacity", "info", String.valueOf(teamSizes.get(0)[1]) + " is the largest team", Math.round(largestTeam) + " active members; compare this with open workload before reallocating.", "/teams"));
        if (employeeGrowth != null) insights.add(new BusinessIntelligenceReportDto.InsightDto("growth", employeeGrowth >= 0 ? "positive" : "warning", "Employee growth is " + (employeeGrowth >= 0 ? "up" : "down") + " " + Math.abs(employeeGrowth) + "%", "Comparison uses the immediately preceding equivalent date range.", "/employees"));
        if (attendanceRate > 0) insights.add(new BusinessIntelligenceReportDto.InsightDto("attendance", attendanceRate >= 85 ? "positive" : "warning", "Attendance is " + attendanceRate + "%", "Latest reporting day across the selected workforce scope.", "/attendance"));

        var activities = auditLogRepository.searchPage(null, null, null, startTime, endTime, PageRequest.of(0, 20)).getContent().stream()
                .map(item -> new BusinessIntelligenceReportDto.ActivityDto(String.valueOf(item.getId()), item.getActorEmail(), item.getAction().name(), item.getEntityType().name(), item.getCreatedAt().toString()))
                .toList();
        var options = new BusinessIntelligenceReportDto.FilterOptionsDto(
                employeeRepository.findDistinctDepartments().stream().map(value -> new BusinessIntelligenceReportDto.OptionDto(value, value)).toList(),
                optionRows(projectRepository.findReportOptions()), optionRows(teamRepository.findReportOptions()), optionRows(employeeRepository.findReportOptions()),
                enumOptions(TaskStatus.values()), enumOptions(CandidatePipelineStatus.values()), enumOptions(LeaveType.values()));
        return new BusinessIntelligenceReportDto(Instant.now(), new BusinessIntelligenceReportDto.DateRangeDto(start.toString(), end.toString()), kpis, charts, insights, risks, activities, options);
    }

    private static BusinessIntelligenceReportDto.MetricDto metric(String key, String label, double value, String unit, Double change, String tone, String context) {
        return new BusinessIntelligenceReportDto.MetricDto(key, label, value, unit, change, tone, context);
    }

    private static List<BusinessIntelligenceReportDto.ChartPointDto> points(List<Object[]> rows) {
        return rows.stream().map(row -> new BusinessIntelligenceReportDto.ChartPointDto(String.valueOf(row[0]), number(row[1]), null, null, null)).toList();
    }

    private static List<BusinessIntelligenceReportDto.ChartPointDto> orderedPoints(List<Object[]> rows, List<String> order) {
        Map<String, Double> values = rowMap(rows);
        List<String> labels = new ArrayList<>(values.keySet());
        labels.sort(Comparator
                .comparingInt((String label) -> {
                    int index = order.indexOf(label);
                    return index < 0 ? Integer.MAX_VALUE : index;
                })
                .thenComparing(String::compareTo));
        return labels.stream()
                .map(label -> new BusinessIntelligenceReportDto.ChartPointDto(label, values.get(label), null, null, null))
                .toList();
    }

    private static List<BusinessIntelligenceReportDto.ChartPointDto> progressPoints(List<Object[]> rows) {
        return rows.stream().map(row -> {
            double total = number(row[2]);
            double completed = number(row[3]);
            double percentage = total == 0 ? 0 : round(completed * 100d / total);
            return new BusinessIntelligenceReportDto.ChartPointDto(
                    String.valueOf(row[1]), percentage, completed, total, String.valueOf(row[0]));
        }).toList();
    }

    private static List<Object[]> canonicalRecruitmentRows(List<Object[]> rows) {
        Map<String, Double> totals = new LinkedHashMap<>();
        rows.forEach(row -> {
            String raw = String.valueOf(row[0]);
            String canonical = switch (raw) {
                case "SCREENING" -> "SHORTLISTED";
                case "TECHNICAL", "HR_REVIEW" -> "INTERVIEW";
                case "WITHDRAWN" -> "REJECTED";
                default -> raw;
            };
            totals.merge(canonical, number(row[1]), Double::sum);
        });
        return totals.entrySet().stream().map(entry -> new Object[] { entry.getKey(), entry.getValue() }).toList();
    }

    private static List<BusinessIntelligenceReportDto.ChartPointDto> triplePoints(List<Object[]> rows) {
        return rows.stream().map(row -> new BusinessIntelligenceReportDto.ChartPointDto(String.valueOf(row[0]), number(row[1]), row.length > 2 ? number(row[2]) : null, row.length > 3 ? number(row[3]) : null, null)).toList();
    }

    private static Map<String, Double> rowMap(List<Object[]> rows) {
        Map<String, Double> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(String.valueOf(row[0]), number(row[1])));
        return result;
    }

    private static double sum(List<Object[]> rows) { return rows.stream().mapToDouble(row -> number(row[1])).sum(); }
    private static double number(Object value) { return value instanceof Number n ? n.doubleValue() : 0d; }
    private static double round(double value) { return Math.round(value * 10d) / 10d; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new BadRequestException(field + " has an unsupported value"); }
    }

    private static List<BusinessIntelligenceReportDto.OptionDto> optionRows(List<Object[]> rows) {
        return rows.stream().map(row -> new BusinessIntelligenceReportDto.OptionDto(String.valueOf(row[0]), String.valueOf(row[1]))).toList();
    }

    private static List<BusinessIntelligenceReportDto.OptionDto> enumOptions(Enum<?>[] values) {
        return Arrays.stream(values).map(value -> new BusinessIntelligenceReportDto.OptionDto(value.name(), value.name().replace('_', ' '))).toList();
    }

    @Override
    public List<TaskAssigneeSummaryDto> getTaskCountByAssignee() {
        List<Long> scopedProjectIds = resolveScopedProjectIds();
        List<Object[]> rows = scopedProjectIds == null
                ? taskRepository.countByAssignee()
                : scopedProjectIds.isEmpty() ? List.of() : taskRepository.countByAssigneeForProjects(scopedProjectIds);

        List<TaskAssigneeSummaryDto> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long assigneeId = ((Number) row[0]).longValue();
            long totalTasks = ((Number) row[1]).longValue();

            Employee assignee = employeeRepository.findById(assigneeId).orElse(null);
            if (assignee == null) {
                continue;
            }

            long overdue = scopedProjectIds == null
                    ? taskRepository.countByAssigneeIdAndDueDateBeforeAndStatusNot(
                    assigneeId,
                    LocalDate.now(),
                    TaskStatus.DONE
            )
                    : scopedProjectIds.isEmpty() ? 0L : taskRepository.countByAssigneeIdAndProjectIdInAndDueDateBeforeAndStatusNot(
                    assigneeId,
                    scopedProjectIds,
                    LocalDate.now(),
                    TaskStatus.DONE
            );

            result.add(TaskAssigneeSummaryDto.builder()
                    .assignee(tenantDtoMapper.toEmployeeSimple(assignee))
                    .totalTasks(totalTasks)
                    .overdueTasks(overdue)
                    .build());
        }
        return result;
    }

    @Override
    public List<TaskProjectSummaryDto> getTaskCountByProject() {
        List<Long> scopedProjectIds = resolveScopedProjectIds();
        List<Object[]> rows = scopedProjectIds == null
                ? taskRepository.countByProject()
                : scopedProjectIds.isEmpty() ? List.of() : taskRepository.countByProjectForProjects(scopedProjectIds);

        return rows.stream()
                .map(row -> TaskProjectSummaryDto.builder()
                        .projectId(((Number) row[0]).longValue())
                        .projectName(row[1].toString())
                        .totalTasks(((Number) row[2]).longValue())
                        .build())
                .toList();
    }

    @Override
    public List<ProjectTaskProgressDto> getProjectProgressSummary() {
        List<Long> scopedProjectIds = resolveScopedProjectIds();
        List<Object[]> rows = scopedProjectIds == null
                ? taskRepository.summarizeProjectProgress(TaskStatus.DONE)
                : scopedProjectIds.isEmpty() ? List.of() : taskRepository.summarizeProjectProgressForProjects(scopedProjectIds, TaskStatus.DONE);
        return rows.stream()
                .map(row -> {
                    long total = ((Number) row[2]).longValue();
                    long done = ((Number) row[3]).longValue();
                    double completionPercent = total == 0 ? 0d : ((double) done * 100.0) / total;
                    return ProjectTaskProgressDto.builder()
                            .projectId(((Number) row[0]).longValue())
                            .projectName(row[1].toString())
                            .totalTasks(total)
                            .doneTasks(done)
                            .completionPercent(Math.round(completionPercent * 100.0) / 100.0)
                            .build();
                })
                .sorted(Comparator.comparing(ProjectTaskProgressDto::getCompletionPercent).reversed())
                .toList();
    }

    @Override
    public List<LeaveTrendPointDto> getLeaveTrend(int year) {
        int currentYear = LocalDate.now().getYear();
        if (year < 2000 || year > currentYear + 1) {
            throw new BadRequestException("Year is out of supported range");
        }

        Map<String, Map<String, Long>> grouped = new LinkedHashMap<>();
        for (Object[] row : leaveRequestRepository.findLeaveTrendByYear(year)) {
            String month = row[0].toString();
            String status = row[1].toString();
            long count = ((Number) row[2]).longValue();
            grouped.computeIfAbsent(month, key -> new HashMap<>()).put(status, count);
        }

        List<LeaveTrendPointDto> points = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            String monthKey = YearMonth.of(year, month).toString();
            Map<String, Long> byStatus = grouped.getOrDefault(monthKey, Map.of());
            long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
            points.add(LeaveTrendPointDto.builder()
                    .month(monthKey)
                    .total(total)
                    .pending(byStatus.getOrDefault(LeaveStatus.PENDING.name(), 0L))
                    .approved(byStatus.getOrDefault(LeaveStatus.APPROVED.name(), 0L))
                    .rejected(byStatus.getOrDefault(LeaveStatus.REJECTED.name(), 0L))
                    .build());
        }
        return points;
    }

    @Override
    public List<AttendanceTrendPointDto> getAttendanceTrend(LocalDate fromDate, LocalDate toDate) {
        LocalDate start = fromDate == null ? LocalDate.now().minusDays(29) : fromDate;
        LocalDate end = toDate == null ? LocalDate.now() : toDate;
        if (end.isBefore(start)) {
            throw new BadRequestException("toDate cannot be before fromDate");
        }
        if (start.plusDays(366).isBefore(end)) {
            throw new BadRequestException("Date range is too large. Maximum supported range is 366 days");
        }

        Map<LocalDate, List<com.worknest.tenant.entity.AttendanceRecord>> grouped = new LinkedHashMap<>();
        attendanceRecordRepository.findByWorkDateBetweenOrderByWorkDateAsc(start, end)
                .forEach(record -> grouped.computeIfAbsent(record.getWorkDate(), ignored -> new ArrayList<>()).add(record));

        List<AttendanceTrendPointDto> result = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            List<com.worknest.tenant.entity.AttendanceRecord> records = grouped.getOrDefault(cursor, List.of());
            result.add(AttendanceTrendPointDto.builder()
                    .workDate(cursor)
                    .presentCount(records.stream().filter(record -> record.getStatus() == AttendanceStatus.PRESENT && !record.isLate()).count())
                    .lateCount(records.stream().filter(com.worknest.tenant.entity.AttendanceRecord::isLate).count())
                    .halfDayCount(records.stream().filter(record -> record.getStatus() == AttendanceStatus.HALF_DAY).count())
                    .incompleteCount(records.stream().filter(record -> record.getStatus() == AttendanceStatus.INCOMPLETE).count())
                    .absentCount(records.stream().filter(record -> record.getStatus() == AttendanceStatus.ABSENT).count())
                    .build());
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    @Override
    public List<EmployeeRoleDistributionDto> getEmployeeRoleDistribution() {
        return employeeRepository.countByRole().stream()
                .map(row -> EmployeeRoleDistributionDto.builder()
                        .role((PlatformRole) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();
    }

    @Override
    public List<EmployeeDesignationDistributionDto> getEmployeeDesignationDistribution() {
        return employeeRepository.countByDesignation().stream()
                .map(row -> EmployeeDesignationDistributionDto.builder()
                        .designation(row[0].toString())
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();
    }

    private List<Long> resolveScopedProjectIds() {
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        if (role == PlatformRole.TENANT_ADMIN || role == PlatformRole.ADMIN || role == PlatformRole.HR) {
            return null;
        }
        if (role != PlatformRole.MANAGER) {
            return List.of();
        }

        Employee manager = getCurrentEmployeeOrThrow();
        List<Long> managedTeamIds = teamRepository.findByManagerId(manager.getId())
                .stream()
                .map(Team::getId)
                .toList();
        if (managedTeamIds.isEmpty()) {
            return List.of();
        }

        return projectTeamRepository.findByTeamIdIn(managedTeamIds).stream()
                .map(ProjectTeam::getProject)
                .map(Project::getId)
                .distinct()
                .toList();
    }

    private Employee getCurrentEmployeeOrThrow() {
        String email = securityUtils.getCurrentUserEmailOrThrow();
        return employeeRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Current user does not have an employee profile"));
    }
}
