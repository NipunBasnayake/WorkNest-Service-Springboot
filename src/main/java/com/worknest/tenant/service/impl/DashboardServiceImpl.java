package com.worknest.tenant.service.impl;

import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.attendance.AttendanceMonthlySummaryDto;
import com.worknest.tenant.dto.dashboard.*;
import com.worknest.tenant.entity.*;
import com.worknest.tenant.enums.*;
import com.worknest.tenant.repository.*;
import com.worknest.tenant.service.AttendanceService;
import com.worknest.tenant.service.DashboardService;
import org.springframework.data.domain.PageRequest;
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

    private static final int RECENT_ITEMS_LIMIT = 5;
    private static final int DUE_SOON_WINDOW_DAYS = 7;
    private static final List<ProjectStatus> ACTIVE_PROJECT_STATUSES = List.of(
            ProjectStatus.PLANNED,
            ProjectStatus.IN_PROGRESS,
            ProjectStatus.ON_HOLD
    );

    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TaskRepository taskRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
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
            AnnouncementRepository announcementRepository,
            NotificationRepository notificationRepository,
            AttendanceService attendanceService,
            SecurityUtils securityUtils) {
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.projectRepository = projectRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.taskRepository = taskRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.announcementRepository = announcementRepository;
        this.notificationRepository = notificationRepository;
        this.attendanceService = attendanceService;
        this.securityUtils = securityUtils;
    }

    @Override
    public TenantAdminDashboardDto getTenantAdminSummary() {
        LocalDate today = LocalDate.now();
        long totalEmployees = employeeRepository.count();
        long activeEmployees = employeeRepository.countByStatus(UserStatus.ACTIVE);
        long totalTasks = taskRepository.count();
        long completedTasks = taskRepository.countByStatus(TaskStatus.DONE);
        AttendanceOverviewDto todayAttendance = buildTodayAttendanceOverview(today, activeEmployees);
        List<DashboardAnnouncementItemDto> recentAnnouncements = getRecentAnnouncements();

        Optional<Employee> currentEmployee = getCurrentEmployeeOptional();
        long myUnreadNotifications = currentEmployee
                .map(employee -> notificationRepository.countByRecipientIdAndReadFalse(employee.getId()))
                .orElse(0L);
        List<DashboardNotificationItemDto> recentNotifications = currentEmployee
                .map(employee -> getRecentNotifications(employee.getId()))
                .orElse(List.of());

        return TenantAdminDashboardDto.builder()
                .totalEmployees(totalEmployees)
                .activeEmployees(activeEmployees)
                .inactiveEmployees(employeeRepository.countByStatus(UserStatus.INACTIVE))
                .totalTeams(teamRepository.count())
                .activeTeams(teamRepository.countActiveTeams(UserStatus.ACTIVE))
                .totalProjects(projectRepository.count())
                .activeProjects(projectRepository.countByStatusIn(ACTIVE_PROJECT_STATUSES))
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .taskCompletionRate(calculatePercent(completedTasks, totalTasks))
                .overdueTasks(taskRepository.countByDueDateBeforeAndStatusNot(today, TaskStatus.DONE))
                .pendingLeaveRequests(leaveRequestRepository.countByStatus(LeaveStatus.PENDING))
                .totalAnnouncements(announcementRepository.count())
                .myUnreadNotifications(myUnreadNotifications)
                .todayAttendanceMarked(todayAttendance.getTotalRecords())
                .todayAttendanceAbsent(todayAttendance.getAbsentCount())
                .projectsByStatus(buildStatusCounts(ProjectStatus.values(), projectRepository.countByStatusGroup()))
                .tasksByStatus(buildStatusCounts(TaskStatus.values(), taskRepository.countByStatusGroup()))
                .leavesByStatus(buildStatusCounts(LeaveStatus.values(), leaveRequestRepository.countByStatusGroup()))
                .recentAnnouncements(recentAnnouncements)
                .recentNotifications(recentNotifications)
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
        long activeEmployees = employeeRepository.countByStatus(UserStatus.ACTIVE);
        AttendanceOverviewDto todayAttendance = buildTodayAttendanceOverview(LocalDate.now(), activeEmployees);

        List<StatusCountDto> roleCounts = employeeRepository.countByRole().stream()
                .map(row -> StatusCountDto.builder()
                        .status(row[0].toString())
                        .count(((Number) row[1]).longValue())
                        .build())
                .toList();

        return HrDashboardDto.builder()
                .totalEmployees(employeeRepository.count())
                .activeEmployees(activeEmployees)
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
        Long currentEmployeeId = currentEmployee.getId();

        LocalDate now = LocalDate.now();
        LocalDate dueSoonEnd = now.plusDays(DUE_SOON_WINDOW_DAYS);
        AttendanceMonthlySummaryDto monthSummary = attendanceService.getMonthlySummary(
                currentEmployeeId,
                now.getYear(),
                now.getMonthValue()
        );
        long myTasksTotal = taskRepository.countByAssigneeId(currentEmployeeId);
        long myCompletedTasks = taskRepository.countByAssigneeIdAndStatus(currentEmployeeId, TaskStatus.DONE);
        long myOverdueTasks = taskRepository.countByAssigneeIdAndDueDateBeforeAndStatusNot(
                currentEmployeeId,
                now,
                TaskStatus.DONE
        );
        long myDueSoonTasks = taskRepository.countByAssigneeIdAndDueDateBetweenAndStatusNot(
                currentEmployeeId,
                now,
                dueSoonEnd,
                TaskStatus.DONE
        );

        List<DashboardTaskItemDto> dueSoonTasks = taskRepository.findDueSoonTasks(
                        currentEmployeeId,
                        TaskStatus.DONE,
                        now,
                        dueSoonEnd,
                        PageRequest.of(0, RECENT_ITEMS_LIMIT))
                .stream()
                .map(this::toDashboardTaskItem)
                .toList();

        List<DashboardTaskItemDto> overdueTaskItems = taskRepository.findOverdueTasks(
                        currentEmployeeId,
                        TaskStatus.DONE,
                        now,
                        PageRequest.of(0, RECENT_ITEMS_LIMIT))
                .stream()
                .map(this::toDashboardTaskItem)
                .toList();

        List<DashboardLeaveItemDto> recentLeaves = leaveRequestRepository
                .findByEmployeeIdOrderByCreatedAtDesc(currentEmployeeId, PageRequest.of(0, RECENT_ITEMS_LIMIT))
                .getContent()
                .stream()
                .map(this::toDashboardLeaveItem)
                .toList();

        DashboardAttendanceSnapshotDto latestAttendance = attendanceRecordRepository
                .findFirstByEmployeeIdOrderByWorkDateDesc(currentEmployeeId)
                .map(this::toDashboardAttendanceSnapshot)
                .orElse(null);

        long myUnreadNotifications = notificationRepository.countByRecipientIdAndReadFalse(currentEmployeeId);

        return EmployeeDashboardDto.builder()
                .employeeId(currentEmployeeId)
                .myTasksTotal(myTasksTotal)
                .myCompletedTasks(myCompletedTasks)
                .myTaskCompletionRate(calculatePercent(myCompletedTasks, myTasksTotal))
                .myOverdueTasks(myOverdueTasks)
                .myDueSoonTasks(myDueSoonTasks)
                .myPendingLeaves(leaveRequestRepository.countByEmployeeIdAndStatus(currentEmployeeId, LeaveStatus.PENDING))
                .myTasksByStatus(buildStatusCounts(TaskStatus.values(), taskRepository.countMyTasksByStatus(currentEmployeeId)))
                .myLeavesByStatus(buildStatusCounts(LeaveStatus.values(),
                        leaveRequestRepository.countByStatusGroupForEmployee(currentEmployeeId)))
                .dueSoonTasks(dueSoonTasks)
                .overdueTaskItems(overdueTaskItems)
                .recentLeaveRequests(recentLeaves)
                .latestAttendance(latestAttendance)
                .recentAnnouncements(getRecentAnnouncements())
                .myUnreadNotifications(myUnreadNotifications)
                .recentNotifications(getRecentNotifications(currentEmployeeId))
                .currentMonthAttendance(monthSummary)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private AttendanceOverviewDto buildTodayAttendanceOverview(LocalDate today, long activeEmployees) {
        long presentCount = attendanceRecordRepository.countByWorkDateAndStatus(today, AttendanceStatus.PRESENT);
        long lateCount = attendanceRecordRepository.countByWorkDateAndLateTrue(today);
        long halfDayCount = attendanceRecordRepository.countByWorkDateAndStatus(today, AttendanceStatus.HALF_DAY);
        long incompleteCount = attendanceRecordRepository.countByWorkDateAndStatus(today, AttendanceStatus.INCOMPLETE);
        long totalRecords = attendanceRecordRepository.countByWorkDate(today);
        long absentCount = Math.max(activeEmployees - totalRecords, 0L);

        return AttendanceOverviewDto.builder()
                .date(today)
                .totalRecords(totalRecords)
                .presentCount(presentCount)
                .lateCount(lateCount)
                .halfDayCount(halfDayCount)
                .incompleteCount(incompleteCount)
                .absentCount(absentCount)
                .attendanceRatePercent(calculatePercent(totalRecords, activeEmployees))
                .build();
    }

    private Employee getCurrentEmployeeOrThrow() {
        return getCurrentEmployeeOptional()
                .orElseThrow(() -> new ResourceNotFoundException("Current user does not have an employee profile"));
    }

    private Optional<Employee> getCurrentEmployeeOptional() {
        PlatformUserPrincipal principal = securityUtils.getCurrentPrincipalOrThrow();
        if (principal.getId() != null) {
            Optional<Employee> byPlatformUserId = employeeRepository.findByPlatformUserId(principal.getId());
            if (byPlatformUserId.isPresent()) {
                return byPlatformUserId;
            }
        }

        String currentEmail = principal.getEmail();
        if (currentEmail == null || currentEmail.isBlank()) {
            return Optional.empty();
        }

        return employeeRepository.findByEmailIgnoreCase(currentEmail);
    }

    private List<DashboardAnnouncementItemDto> getRecentAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, RECENT_ITEMS_LIMIT))
                .stream()
                .map(this::toDashboardAnnouncementItem)
                .toList();
    }

    private List<DashboardNotificationItemDto> getRecentNotifications(Long employeeId) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(employeeId, PageRequest.of(0, RECENT_ITEMS_LIMIT))
                .stream()
                .map(this::toDashboardNotificationItem)
                .toList();
    }

    private DashboardTaskItemDto toDashboardTaskItem(Task task) {
        return DashboardTaskItemDto.builder()
                .id(task.getId())
                .projectId(task.getProject().getId())
                .projectName(task.getProject().getName())
                .title(task.getTitle())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .build();
    }

    private DashboardLeaveItemDto toDashboardLeaveItem(LeaveRequest leaveRequest) {
        return DashboardLeaveItemDto.builder()
                .id(leaveRequest.getId())
                .leaveType(leaveRequest.getLeaveType())
                .status(leaveRequest.getStatus())
                .startDate(leaveRequest.getStartDate())
                .endDate(leaveRequest.getEndDate())
                .createdAt(leaveRequest.getCreatedAt())
                .build();
    }

    private DashboardAnnouncementItemDto toDashboardAnnouncementItem(Announcement announcement) {
        return DashboardAnnouncementItemDto.builder()
                .id(announcement.getId())
                .title(announcement.getTitle())
                .message(announcement.getMessage())
                .createdByName(formatEmployeeName(announcement.getCreatedBy()))
                .createdAt(announcement.getCreatedAt())
                .build();
    }

    private DashboardNotificationItemDto toDashboardNotificationItem(Notification notification) {
        return DashboardNotificationItemDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .message(notification.getMessage())
                .read(notification.isRead())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private DashboardAttendanceSnapshotDto toDashboardAttendanceSnapshot(AttendanceRecord attendanceRecord) {
        return DashboardAttendanceSnapshotDto.builder()
                .workDate(attendanceRecord.getWorkDate())
                .checkIn(attendanceRecord.getCheckIn())
                .checkOut(attendanceRecord.getCheckOut())
                .status(attendanceRecord.getStatus())
                .build();
    }

    private String formatEmployeeName(Employee employee) {
        if (employee == null) {
            return null;
        }
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? employee.getEmail() : fullName;
    }

    private double calculatePercent(long part, long total) {
        if (total <= 0) {
            return 0d;
        }
        double percentage = ((double) part * 100.0) / total;
        return Math.round(percentage * 100.0) / 100.0;
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
