package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
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
import com.worknest.tenant.enums.TaskStatus;
import com.worknest.tenant.repository.*;
import com.worknest.tenant.service.AnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
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
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper) {
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.projectRepository = projectRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.taskRepository = taskRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
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
        List<Project> projects;
        List<Long> scopedProjectIds = resolveScopedProjectIds();
        if (scopedProjectIds == null) {
            projects = projectRepository.findAll();
        } else if (scopedProjectIds.isEmpty()) {
            projects = List.of();
        } else {
            projects = projectRepository.findAllById(scopedProjectIds);
        }

        return projects.stream()
                .map(project -> {
                    long total = taskRepository.countByProjectId(project.getId());
                    long done = taskRepository.countByProjectIdAndStatus(project.getId(), TaskStatus.DONE);
                    double completionPercent = total == 0 ? 0d : ((double) done * 100.0) / total;
                    return ProjectTaskProgressDto.builder()
                            .projectId(project.getId())
                            .projectName(project.getName())
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

        Map<LocalDate, Map<String, Long>> grouped = new LinkedHashMap<>();
        List<Object[]> rows = attendanceRecordRepository.findTrendCounts(start, end);
        for (Object[] row : rows) {
            LocalDate day = (LocalDate) row[0];
            String status = row[1].toString();
            long count = ((Number) row[2]).longValue();
            grouped.computeIfAbsent(day, ignored -> new HashMap<>()).put(status, count);
        }

        List<AttendanceTrendPointDto> result = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            Map<String, Long> counts = grouped.getOrDefault(cursor, Map.of());
            result.add(AttendanceTrendPointDto.builder()
                    .workDate(cursor)
                    .presentCount(counts.getOrDefault(AttendanceStatus.PRESENT.name(), 0L))
                    .halfDayCount(counts.getOrDefault(AttendanceStatus.HALF_DAY.name(), 0L))
                    .incompleteCount(counts.getOrDefault(AttendanceStatus.INCOMPLETE.name(), 0L))
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
