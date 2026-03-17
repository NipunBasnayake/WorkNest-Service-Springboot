package com.worknest.tenant.service.impl;

import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.attendance.AttendanceMonthlySummaryDto;
import com.worknest.tenant.dto.dashboard.*;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Project;
import com.worknest.tenant.entity.ProjectTeam;
import com.worknest.tenant.enums.AttendanceStatus;
import com.worknest.tenant.enums.LeaveStatus;
import com.worknest.tenant.enums.ProjectStatus;
import com.worknest.tenant.enums.TaskStatus;
import com.worknest.tenant.repository.*;
import com.worknest.tenant.service.AttendanceService;
import com.worknest.tenant.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "transactionManager", readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TaskRepository taskRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceService attendanceService;
    private final SecurityUtils securityUtils;

    public DashboardServiceImpl(
            EmployeeRepository employeeRepository,
            TeamRepository teamRepository,
            ProjectRepository projectRepository,
            ProjectTeamRepository projectTeamRepository,
            TaskRepository taskRepository,
            LeaveRequestRepository leaveRequestRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceService attendanceService,
            SecurityUtils securityUtils) {
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.projectRepository = projectRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.taskRepository = taskRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceService = attendanceService;
        this.securityUtils = securityUtils;
    }

    @Override
    public TenantAdminDashboardDto getTenantAdminSummary() {
        AttendanceOverviewDto todayAttendance = buildTodayAttendanceOverview();

        return TenantAdminDashboardDto.builder()
                .totalEmployees(employeeRepository.count())
                .activeEmployees(employeeRepository.countByStatus(UserStatus.ACTIVE))
                .inactiveEmployees(employeeRepository.countByStatus(UserStatus.INACTIVE))
                .totalTeams(teamRepository.count())
                .totalProjects(projectRepository.count())
                .totalTasks(taskRepository.count())
                .overdueTasks(taskRepository.countByDueDateBeforeAndStatusNot(LocalDate.now(), TaskStatus.DONE))
                .pendingLeaveRequests(leaveRequestRepository.countByStatus(LeaveStatus.PENDING))
                .projectsByStatus(buildStatusCounts(ProjectStatus.values(), projectRepository.countByStatusGroup()))
                .tasksByStatus(buildStatusCounts(TaskStatus.values(), taskRepository.countByStatusGroup()))
                .leavesByStatus(buildStatusCounts(LeaveStatus.values(), leaveRequestRepository.countByStatusGroup()))
                .todayAttendance(todayAttendance)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public ManagerDashboardDto getManagerSummary() {
        Employee manager = getCurrentEmployeeOrThrow();
        List<Long> managedTeamIds = teamRepository.findByManagerId(manager.getId())
                .stream()
                .map(team -> team.getId())
                .toList();

        if (managedTeamIds.isEmpty()) {
            return ManagerDashboardDto.builder()
                    .managerEmployeeId(manager.getId())
                    .managedTeams(0)
                    .managedProjects(0)
                    .totalProjectTasks(0)
                    .overdueProjectTasks(0)
                    .tasksByStatus(buildStatusCounts(TaskStatus.values(), List.of()))
                    .projectTaskProgress(List.of())
                    .generatedAt(LocalDateTime.now())
                    .build();
        }

        List<ProjectTeam> projectTeams = projectTeamRepository.findByTeamIdIn(managedTeamIds);
        List<Project> managedProjects = projectTeams.stream()
                .map(ProjectTeam::getProject)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(Project::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())
                ));
        List<Long> managedProjectIds = managedProjects.stream().map(Project::getId).toList();

        if (managedProjectIds.isEmpty()) {
            return ManagerDashboardDto.builder()
                    .managerEmployeeId(manager.getId())
                    .managedTeams(managedTeamIds.size())
                    .managedProjects(0)
                    .totalProjectTasks(0)
                    .overdueProjectTasks(0)
                    .tasksByStatus(buildStatusCounts(TaskStatus.values(), List.of()))
                    .projectTaskProgress(List.of())
                    .generatedAt(LocalDateTime.now())
                    .build();
        }

        List<ProjectTaskProgressDto> projectProgress = managedProjects.stream()
                .map(project -> {
                    long total = taskRepository.countByProjectId(project.getId());
                    long done = taskRepository.countByProjectIdAndStatus(project.getId(), TaskStatus.DONE);
                    double completion = total == 0 ? 0d : ((double) done * 100.0) / total;
                    return ProjectTaskProgressDto.builder()
                            .projectId(project.getId())
                            .projectName(project.getName())
                            .totalTasks(total)
                            .doneTasks(done)
                            .completionPercent(Math.round(completion * 100.0) / 100.0)
                            .build();
                })
                .toList();

        return ManagerDashboardDto.builder()
                .managerEmployeeId(manager.getId())
                .managedTeams(managedTeamIds.size())
                .managedProjects(managedProjectIds.size())
                .totalProjectTasks(taskRepository.countByProjectIdIn(managedProjectIds))
                .overdueProjectTasks(taskRepository.countByProjectIdInAndDueDateBeforeAndStatusNot(
                        managedProjectIds,
                        LocalDate.now(),
                        TaskStatus.DONE
                ))
                .tasksByStatus(buildStatusCounts(TaskStatus.values(),
                        taskRepository.countByStatusGroupForProjects(managedProjectIds)))
                .projectTaskProgress(projectProgress)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public HrDashboardDto getHrSummary() {
        AttendanceOverviewDto todayAttendance = buildTodayAttendanceOverview();

        List<StatusCountDto> roleCounts = employeeRepository.countByRole().stream()
                .map(row -> StatusCountDto.builder()
                        .status(row[0].toString())
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();

        return HrDashboardDto.builder()
                .totalEmployees(employeeRepository.count())
                .activeEmployees(employeeRepository.countByStatus(UserStatus.ACTIVE))
                .pendingLeaveRequests(leaveRequestRepository.countByStatus(LeaveStatus.PENDING))
                .leavesByStatus(buildStatusCounts(LeaveStatus.values(), leaveRequestRepository.countByStatusGroup()))
                .todayAttendance(todayAttendance)
                .employeesByRole(roleCounts)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public EmployeeDashboardDto getMySummary() {
        Employee currentEmployee = getCurrentEmployeeOrThrow();

        LocalDate now = LocalDate.now();
        AttendanceMonthlySummaryDto monthSummary = attendanceService.getMonthlySummary(
                currentEmployee.getId(),
                now.getYear(),
                now.getMonthValue()
        );

        return EmployeeDashboardDto.builder()
                .employeeId(currentEmployee.getId())
                .myTasksTotal(taskRepository.countByAssigneeId(currentEmployee.getId()))
                .myOverdueTasks(taskRepository.countByAssigneeIdAndDueDateBeforeAndStatusNot(
                        currentEmployee.getId(),
                        LocalDate.now(),
                        TaskStatus.DONE
                ))
                .myPendingLeaves(leaveRequestRepository.countByEmployeeIdAndStatus(currentEmployee.getId(), LeaveStatus.PENDING))
                .myTasksByStatus(buildStatusCounts(TaskStatus.values(), taskRepository.countMyTasksByStatus(currentEmployee.getId())))
                .currentMonthAttendance(monthSummary)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private AttendanceOverviewDto buildTodayAttendanceOverview() {
        LocalDate today = LocalDate.now();
        return AttendanceOverviewDto.builder()
                .date(today)
                .presentCount(attendanceRecordRepository.countByWorkDateAndStatus(today, AttendanceStatus.PRESENT))
                .halfDayCount(attendanceRecordRepository.countByWorkDateAndStatus(today, AttendanceStatus.HALF_DAY))
                .incompleteCount(attendanceRecordRepository.countByWorkDateAndStatus(today, AttendanceStatus.INCOMPLETE))
                .build();
    }

    private Employee getCurrentEmployeeOrThrow() {
        String currentEmail = securityUtils.getCurrentUserEmailOrThrow();
        return employeeRepository.findByEmailIgnoreCase(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Current user does not have an employee profile"));
    }

    private <E extends Enum<E>> List<StatusCountDto> buildStatusCounts(E[] values, List<Object[]> rawRows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rawRows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            counts.put(row[0].toString(), ((Number) row[1]).longValue());
        }

        List<StatusCountDto> response = new ArrayList<>();
        for (E value : values) {
            response.add(StatusCountDto.builder()
                    .status(value.name())
                    .count(counts.getOrDefault(value.name(), 0L))
                    .build());
        }
        return response;
    }
}
