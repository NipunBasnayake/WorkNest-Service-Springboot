package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.task.KanbanBoardResponseDto;
import com.worknest.tenant.dto.task.KanbanColumnDto;
import com.worknest.tenant.dto.task.TaskAssigneeUpdateRequestDto;
import com.worknest.tenant.dto.task.TaskCommentCreateRequestDto;
import com.worknest.tenant.dto.task.TaskCommentResponseDto;
import com.worknest.tenant.dto.task.TaskCreateRequestDto;
import com.worknest.tenant.dto.task.TaskDueDateUpdateRequestDto;
import com.worknest.tenant.dto.task.TaskPriorityUpdateRequestDto;
import com.worknest.tenant.dto.task.TaskResponseDto;
import com.worknest.tenant.dto.task.TaskStatusUpdateRequestDto;
import com.worknest.tenant.dto.task.TaskUpdateRequestDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Project;
import com.worknest.tenant.entity.Task;
import com.worknest.tenant.entity.TaskComment;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.enums.AttachmentEntityType;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.enums.ProjectStatus;
import com.worknest.tenant.enums.TaskStatus;
import com.worknest.tenant.enums.TeamFunctionalRole;
import com.worknest.tenant.repository.AttachmentRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.ProjectRepository;
import com.worknest.tenant.repository.ProjectTeamRepository;
import com.worknest.tenant.repository.TaskCommentRepository;
import com.worknest.tenant.repository.TaskRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import com.worknest.tenant.service.TaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(transactionManager = "transactionManager")
public class TaskServiceImpl implements TaskService {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS),
            TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.TODO, TaskStatus.IN_REVIEW),
            TaskStatus.IN_REVIEW, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.BLOCKED),
            TaskStatus.BLOCKED, EnumSet.of(TaskStatus.IN_REVIEW),
            TaskStatus.DONE, EnumSet.of(TaskStatus.IN_REVIEW)
    );

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final AttachmentRepository attachmentRepository;
    private final AuthorizationService authorizationService;
    private final TenantDtoMapper tenantDtoMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EmailNotificationService emailNotificationService;
    private final TenantRealtimePublisher tenantRealtimePublisher;

    public TaskServiceImpl(
            TaskRepository taskRepository,
            TaskCommentRepository taskCommentRepository,
            ProjectRepository projectRepository,
            ProjectTeamRepository projectTeamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            EmployeeRepository employeeRepository,
            AttachmentRepository attachmentRepository,
            AuthorizationService authorizationService,
            TenantDtoMapper tenantDtoMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            EmailNotificationService emailNotificationService,
            TenantRealtimePublisher tenantRealtimePublisher) {
        this.taskRepository = taskRepository;
        this.taskCommentRepository = taskCommentRepository;
        this.projectRepository = projectRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.employeeRepository = employeeRepository;
        this.attachmentRepository = attachmentRepository;
        this.authorizationService = authorizationService;
        this.tenantDtoMapper = tenantDtoMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.emailNotificationService = emailNotificationService;
        this.tenantRealtimePublisher = tenantRealtimePublisher;
    }

    @Override
    public TaskResponseDto createTask(TaskCreateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.CREATE_TASK);
        Project project = getProjectOrThrow(requestDto.getProjectId());
        ensureProjectAllowsTaskCreation(project);
        Team assignedTeam = resolveAssignedTeamOrThrow(project.getId(), requestDto.getAssignedTeamId());
        enforceTaskCreationAccess(project, assignedTeam.getId());

        Long currentUserId = authorizationService.getCurrentUserIdOrThrow();
        Employee creator = resolveCurrentEmployeeOrNull();
        Employee assignee = resolveAssignedEmployee(assignedTeam, resolveRequestedAssignedEmployeeId(requestDto));

        validateDueDate(project, requestDto.getDueDate());

        Task task = new Task();
        task.setProject(project);
        task.setTitle(requestDto.getTitle().trim());
        task.setDescription(trimToNull(requestDto.getDescription()));
        task.setStatus(requestDto.getStatus());
        task.setPriority(requestDto.getPriority());
        task.setAssignee(assignee);
        task.setAssignedTeam(assignedTeam);
        task.setCreatedBy(creator);
        task.setCreatedByUserId(currentUserId);
        task.setAssignedByUserId(currentUserId);
        task.setDueDate(requestDto.getDueDate());

        Task saved = taskRepository.save(task);
        notifyTaskAssignment(saved);
        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.TASK,
                saved.getId(),
                "{\"title\":\"" + escapeJson(saved.getTitle()) + "\"}"
        );
        publishTaskRealtime(saved);
        return toTaskResponse(saved);
    }

    @Override
    public TaskResponseDto updateTask(Long taskId, TaskUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.MANAGE_TASK);
        Task task = getTaskOrThrow(taskId);
        enforceTaskManagerAccess(task);
        Long previousAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
        TaskStatus previousStatus = task.getStatus();
        String previousTitle = task.getTitle();
        String previousDescription = task.getDescription();
        LocalDate previousDueDate = task.getDueDate();

        if (requestDto.getStatus() != null) {
            validateTaskTransition(task, requestDto.getStatus());
            task.setStatus(requestDto.getStatus());
        }
        if (requestDto.getPriority() != null) {
            task.setPriority(requestDto.getPriority());
        }
        if (requestDto.getAssignedTeamId() != null) {
            Team assignedTeam = resolveAssignedTeamOrThrow(task.getProject().getId(), requestDto.getAssignedTeamId());
            task.setAssignedTeam(assignedTeam);
            task.setAssignedByUserId(authorizationService.getCurrentUserIdOrThrow());
        }

        Long requestedAssigneeId = resolveRequestedAssignedEmployeeId(requestDto);
        if (requestDto.getAssignedEmployeeId() != null || requestDto.getAssigneeId() != null) {
            task.setAssignee(resolveAssignedEmployee(task.getAssignedTeam(), requestedAssigneeId));
            task.setAssignedByUserId(authorizationService.getCurrentUserIdOrThrow());
        }

        task.setTitle(requestDto.getTitle().trim());
        task.setDescription(trimToNull(requestDto.getDescription()));

        validateDueDate(task.getProject(), requestDto.getDueDate());
        task.setDueDate(requestDto.getDueDate());
        Task updated = taskRepository.save(task);
        if (assigneeChanged(previousAssigneeId, updated)) {
            notifyTaskAssignment(updated);
        }
        if (statusChanged(previousStatus, updated)) {
            notifyTaskStatusChange(updated);
        }
        if (hasTaskContentChanged(previousTitle, previousDescription, previousDueDate, updated)
                && !statusChanged(previousStatus, updated)) {
            notifyTaskUpdated(updated);
        }
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"title\":\"" + escapeJson(updated.getTitle()) + "\"}"
        );
        publishTaskRealtime(updated);
        return toTaskResponse(updated);
    }

    @Override
    public TaskResponseDto changeStatus(Long taskId, TaskStatusUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.UPDATE_TASK_STATUS);
        Task task = getTaskOrThrow(taskId);
        validateTaskTransition(task, requestDto.getStatus());
        task.setStatus(requestDto.getStatus());
        Task updated = taskRepository.save(task);
        notifyTaskStatusChange(updated);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"status\":\"" + updated.getStatus() + "\"}"
        );
        publishTaskRealtime(updated);
        return toTaskResponse(updated);
    }

    @Override
    public TaskResponseDto changePriority(Long taskId, TaskPriorityUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.MANAGE_TASK);
        Task task = getTaskOrThrow(taskId);
        enforceTaskManagerAccess(task);
        task.setPriority(requestDto.getPriority());
        Task updated = taskRepository.save(task);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"priority\":\"" + updated.getPriority() + "\"}"
        );
        publishTaskRealtime(updated);
        return toTaskResponse(updated);
    }

    @Override
    public TaskResponseDto changeAssignee(Long taskId, TaskAssigneeUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.ASSIGN_TASK);
        Task task = getTaskOrThrow(taskId);
        enforceTaskAssignmentAccess(task);
        Long assignedEmployeeId = resolveRequestedAssignedEmployeeId(requestDto);
        if (assignedEmployeeId == null) {
            throw new BadRequestException("Assigned employee is required");
        }
        Employee assignee = resolveAssignedEmployee(task.getAssignedTeam(), assignedEmployeeId);
        task.setAssignee(assignee);
        task.setAssignedByUserId(authorizationService.getCurrentUserIdOrThrow());
        Task updated = taskRepository.save(task);
        notifyTaskAssignment(updated);
        auditLogService.logAction(
                AuditActionType.ASSIGN,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"assigneeId\":" + (assignee == null ? "null" : assignee.getId()) + "}"
        );
        publishTaskRealtime(updated);
        return toTaskResponse(updated);
    }

    @Override
    public TaskResponseDto changeDueDate(Long taskId, TaskDueDateUpdateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.MANAGE_TASK);
        Task task = getTaskOrThrow(taskId);
        enforceTaskManagerAccess(task);
        validateDueDate(task.getProject(), requestDto.getDueDate());
        task.setDueDate(requestDto.getDueDate());
        Task updated = taskRepository.save(task);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"dueDate\":\"" + updated.getDueDate() + "\"}"
        );
        publishTaskRealtime(updated);
        return toTaskResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TaskResponseDto> listMyTasks() {
        authorizationService.requirePermission(Permission.VIEW_TASK);
        Long currentEmployeeId = resolveCurrentEmployeeIdOrThrow();
        return taskRepository.findDistinctVisibleByEmployeeOrderByCreatedAtDesc(
                currentEmployeeId,
                TeamFunctionalRole.TEAM_LEAD,
                TeamFunctionalRole.PROJECT_MANAGER)
            .stream()
                .map(this::toTaskResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TaskResponseDto> listByProject(Long projectId) {
        authorizationService.requirePermission(Permission.VIEW_TASK);
        Project project = getProjectOrThrow(projectId);
        enforceProjectReadAccess(project);

        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        Long currentEmployeeId = resolveCurrentEmployeeIdOrNull();

        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(task -> canReadTask(task, role, currentEmployeeId))
                .map(this::toTaskResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<TaskResponseDto> listTasksPaged(
            Long projectId,
            TaskStatus status,
            Long assigneeId,
            LocalDate dueFrom,
            LocalDate dueTo,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        authorizationService.requirePermission(Permission.VIEW_TASK);
        validateDueDateRange(dueFrom, dueTo);

        Long resolvedAssigneeId = resolveRequestedAssigneeForRead(assigneeId);

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<Task> resultPage = taskRepository.search(
                projectId,
                status,
                resolvedAssigneeId,
                dueFrom,
                dueTo,
                trimToNull(search),
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

            PlatformRole role = authorizationService.getCurrentRoleOrThrow();
            Long currentEmployeeId = resolveCurrentEmployeeIdOrNull();
            List<TaskResponseDto> items = resultPage.getContent().stream()
                .filter(task -> canReadTask(task, role, currentEmployeeId))
                .map(this::toTaskResponse)
                .toList();

        return PagedResultDto.<TaskResponseDto>builder()
                .items(items)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(role == PlatformRole.EMPLOYEE ? items.size() : resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TaskResponseDto> listByAssignee(Long assigneeId) {
        authorizationService.requirePermission(Permission.VIEW_TASK);
        Long resolvedAssigneeId = resolveRequestedAssigneeForRead(assigneeId);
        if (resolvedAssigneeId == null) {
            throw new BadRequestException("Assignee ID is required");
        }

        getActiveEmployeeOrThrow(resolvedAssigneeId);
        return taskRepository.findByAssigneeIdOrderByCreatedAtDesc(resolvedAssigneeId).stream()
                .map(this::toTaskResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TaskResponseDto> listByProjectAndStatus(Long projectId, TaskStatus status) {
        authorizationService.requirePermission(Permission.VIEW_TASK);
        Project project = getProjectOrThrow(projectId);
        enforceProjectReadAccess(project);

        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        Long currentEmployeeId = resolveCurrentEmployeeIdOrNull();

        return taskRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status).stream()
                .filter(task -> canReadTask(task, role, currentEmployeeId))
                .map(this::toTaskResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public TaskResponseDto getTaskById(Long taskId) {
        authorizationService.requirePermission(Permission.VIEW_TASK);
        Task task = getTaskOrThrow(taskId);
        enforceTaskReadAccess(task);
        return toTaskResponse(task);
    }

    @Override
    public void deleteTask(Long taskId) {
        authorizationService.requirePermission(Permission.MANAGE_TASK);
        Task task = getTaskOrThrow(taskId);
        enforceTaskManagerAccess(task);

        if (attachmentRepository.existsByEntityTypeAndEntityId(AttachmentEntityType.TASK, taskId)) {
            throw new BadRequestException("Cannot delete task with attachments. Remove attachments first.");
        }

        taskCommentRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.TASK,
                taskId,
                null
        );
    }

    @Override
    public TaskCommentResponseDto addComment(Long taskId, TaskCommentCreateRequestDto requestDto) {
        authorizationService.requirePermission(Permission.VIEW_TASK);
        Task task = getTaskOrThrow(taskId);
        enforceTaskReadAccess(task);

        Long commenterEmployeeId = authorizationService.getCurrentEmployeeIdOrThrow();
        Employee commenter = getActiveEmployeeOrThrow(commenterEmployeeId);

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setCommentedBy(commenter);
        comment.setComment(requestDto.getComment().trim());

        TaskComment saved = taskCommentRepository.save(comment);
        notifyTaskCommentParticipants(task, commenter, saved.getComment());
        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.TASK,
                taskId,
                "{\"commentId\":" + saved.getId() + "}"
        );
        return toTaskCommentResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TaskCommentResponseDto> listComments(Long taskId) {
        authorizationService.requirePermission(Permission.VIEW_TASK);
        Task task = getTaskOrThrow(taskId);
        enforceTaskReadAccess(task);
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toTaskCommentResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public KanbanBoardResponseDto getKanbanBoard(Long projectId) {
        authorizationService.requirePermission(Permission.VIEW_TASK);
        Project project = getProjectOrThrow(projectId);
        enforceProjectReadAccess(project);

        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        Long currentEmployeeId = role == PlatformRole.EMPLOYEE ? resolveCurrentEmployeeIdOrThrow() : null;

        Map<TaskStatus, List<TaskResponseDto>> grouped = new EnumMap<>(TaskStatus.class);
        Map<String, Long> summary = new java.util.LinkedHashMap<>();

        for (TaskStatus status : TaskStatus.values()) {
            List<TaskResponseDto> tasks = taskRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status)
                    .stream()
                    .filter(task -> canReadTask(task, role, currentEmployeeId))
                    .map(this::toTaskResponse)
                    .toList();
            grouped.put(status, tasks);
            summary.put(status.name(), (long) tasks.size());
        }

        List<KanbanColumnDto> columns = grouped.entrySet().stream()
                .map(entry -> KanbanColumnDto.builder()
                        .status(entry.getKey())
                        .tasks(entry.getValue())
                        .build())
                .toList();

        return KanbanBoardResponseDto.builder()
                .projectId(projectId)
                .columns(columns)
                .summary(summary)
                .build();
    }

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
    }

    private Task getTaskOrThrow(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));
    }

    private Employee getActiveEmployeeOrThrow(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (employee.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Employee is not active: " + employeeId);
        }
        return employee;
    }

    private Team resolveAssignedTeamOrThrow(Long projectId, Long assignedTeamId) {
        if (assignedTeamId == null) {
            throw new BadRequestException("Assigned team is required");
        }

        Team assignedTeam = teamRepository.findById(assignedTeamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + assignedTeamId));

        if (!projectTeamRepository.existsByProjectIdAndTeamId(projectId, assignedTeamId)) {
            throw new BadRequestException("Assigned team is not linked to this project");
        }
        return assignedTeam;
    }

    private Employee resolveAssignedEmployee(Team assignedTeam, Long assignedEmployeeId) {
        if (assignedEmployeeId == null) {
            return null;
        }
        if (assignedTeam == null) {
            throw new BadRequestException("Assigned team is required before assigning an employee");
        }

        Employee assignee = getActiveEmployeeOrThrow(assignedEmployeeId);
        if (!isEmployeeActiveInTeam(assignedTeam.getId(), assignedEmployeeId)) {
            throw new BadRequestException("Assigned employee must be an active member of the assigned team");
        }
        return assignee;
    }

    private boolean isEmployeeActiveInTeam(Long teamId, Long employeeId) {
        if (teamId == null || employeeId == null) {
            return false;
        }
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team != null && team.getManager() != null && employeeId.equals(team.getManager().getId())) {
            return true;
        }
        return teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, employeeId).isPresent();
    }

    private Long resolveRequestedAssignedEmployeeId(TaskCreateRequestDto requestDto) {
        return requestDto.getAssignedEmployeeId() != null ? requestDto.getAssignedEmployeeId() : requestDto.getAssigneeId();
    }

    private Long resolveRequestedAssignedEmployeeId(TaskUpdateRequestDto requestDto) {
        return requestDto.getAssignedEmployeeId() != null ? requestDto.getAssignedEmployeeId() : requestDto.getAssigneeId();
    }

    private Long resolveRequestedAssignedEmployeeId(TaskAssigneeUpdateRequestDto requestDto) {
        return requestDto.getAssignedEmployeeId() != null ? requestDto.getAssignedEmployeeId() : requestDto.getAssigneeId();
    }

    private void validateDueDate(Project project, LocalDate dueDate) {
        if (dueDate == null) {
            return;
        }

        if (dueDate.isBefore(LocalDate.now())) {
            throw new BadRequestException("Task due date cannot be in the past");
        }

        if (project.getStartDate() != null && dueDate.isBefore(project.getStartDate())) {
            throw new BadRequestException("Task due date cannot be before project start date");
        }
        if (project.getEndDate() != null && dueDate.isAfter(project.getEndDate())) {
            throw new BadRequestException("Task due date cannot be after project end date");
        }
    }

    private void validateTaskTransition(Task task, TaskStatus newStatus) {
        if (task == null || newStatus == null) {
            throw new BadRequestException("Task status is required");
        }

        TaskStatus currentStatus = task.getStatus();
        if (currentStatus == newStatus) {
            return;
        }

        Set<TaskStatus> allowedTransitions = ALLOWED_STATUS_TRANSITIONS.getOrDefault(
                currentStatus,
                EnumSet.noneOf(TaskStatus.class)
        );
        if (!allowedTransitions.contains(newStatus)) {
            throw new BadRequestException("Invalid task status transition: " + currentStatus + " -> " + newStatus);
        }

        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        if (role.isTenantAdminEquivalent()) {
            return;
        }

        Long currentEmployeeId = resolveCurrentEmployeeIdOrNull();
        if (currentEmployeeId == null) {
            throw new ForbiddenOperationException("You are not allowed to update this task status");
        }

        if (task.getAssignedTeam() == null || task.getAssignedTeam().getId() == null) {
            if (!authorizationService.isTaskAssignee(task)) {
                throw new ForbiddenOperationException("Only the assignee can update this task");
            }
            return;
        }

        Long assignedTeamId = task.getAssignedTeam().getId();
        if (!isTeamManagerOrLead(currentEmployeeId, assignedTeamId)) {
            throw new ForbiddenOperationException("Only team leads or project managers can update team task status");
        }

        if ((currentStatus == TaskStatus.TODO && newStatus == TaskStatus.IN_PROGRESS)
                || (currentStatus == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.TODO)
                || (currentStatus == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.IN_REVIEW)
                || (currentStatus == TaskStatus.IN_REVIEW && newStatus == TaskStatus.IN_PROGRESS)) {
            return;
        }

        throw new ForbiddenOperationException("Team leads and project managers can only move team tasks between TODO, IN_PROGRESS, and REVIEW");
    }

    private void ensureProjectAllowsTaskCreation(Project project) {
        if (project.getStatus() == ProjectStatus.COMPLETED || project.getStatus() == ProjectStatus.CANCELLED) {
            throw new BadRequestException("Cannot create tasks for a project in status: " + project.getStatus());
        }
    }

    private TaskResponseDto toTaskResponse(Task task) {
        return TaskResponseDto.builder()
                .id(task.getId())
                .projectId(task.getProject().getId())
                .projectName(task.getProject().getName())
                .assignedTeamId(task.getAssignedTeam() == null ? null : task.getAssignedTeam().getId())
                .assignedTeamName(task.getAssignedTeam() == null ? null : task.getAssignedTeam().getName())
                .assignedEmployeeId(task.getAssignee() == null ? null : task.getAssignee().getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .assignee(tenantDtoMapper.toEmployeeSimple(task.getAssignee()))
                .createdBy(tenantDtoMapper.toEmployeeSimple(task.getCreatedBy()))
                .createdByEmployeeId(task.getCreatedBy() == null ? null : task.getCreatedBy().getId())
                .createdByUserId(task.getCreatedByUserId())
                .assignedBy(null)
                .assignedByEmployeeId(null)
                .assignedByUserId(task.getAssignedByUserId())
                .dueDate(task.getDueDate())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private TaskCommentResponseDto toTaskCommentResponse(TaskComment taskComment) {
        return TaskCommentResponseDto.builder()
                .id(taskComment.getId())
                .taskId(taskComment.getTask().getId())
                .commentedBy(tenantDtoMapper.toEmployeeSimple(taskComment.getCommentedBy()))
                .comment(taskComment.getComment())
                .createdAt(taskComment.getCreatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean assigneeChanged(Long previousAssigneeId, Task updatedTask) {
        Long newAssigneeId = updatedTask.getAssignee() == null ? null : updatedTask.getAssignee().getId();
        if (previousAssigneeId == null) {
            return newAssigneeId != null;
        }
        return !previousAssigneeId.equals(newAssigneeId);
    }

    private boolean statusChanged(TaskStatus previousStatus, Task updatedTask) {
        return previousStatus != updatedTask.getStatus();
    }

    private void notifyTaskAssignment(Task task) {
        if (task.getAssignee() == null) {
            return;
        }

        if (task.getCreatedBy() != null && task.getCreatedBy().getId().equals(task.getAssignee().getId())) {
            return;
        }

        notificationService.createSystemNotification(
                task.getAssignee().getId(),
                NotificationType.TASK_ASSIGNED,
                "You were assigned task: " + task.getTitle(),
                AuditEntityType.TASK.name(),
                task.getId()
        );
        emailNotificationService.sendTaskAssignedEmail(
                task.getAssignee().getEmail(),
                buildFullName(task.getAssignee()),
                task.getTitle(),
                task.getDescription(),
                task.getDueDate(),
                task.getPriority() == null ? null : task.getPriority().name(),
                buildFullName(task.getAssignee()),
                buildFullName(task.getCreatedBy())
        );
    }

    private void notifyTaskStatusChange(Task task) {
        Set<Long> recipients = new java.util.LinkedHashSet<>();
        if (task.getCreatedBy() != null) {
            recipients.add(task.getCreatedBy().getId());
        }
        if (task.getAssignee() != null) {
            recipients.add(task.getAssignee().getId());
        }

        Employee actor = resolveCurrentEmployeeOrNull();
        if (actor != null) {
            recipients.remove(actor.getId());
        }

        for (Long recipientId : recipients) {
            Employee recipient = employeeRepository.findById(recipientId).orElse(null);
            notificationService.createSystemNotification(
                    recipientId,
                    NotificationType.TASK_STATUS_CHANGED,
                    "Task status changed to " + task.getStatus() + ": " + task.getTitle(),
                    AuditEntityType.TASK.name(),
                    task.getId()
            );
            if (recipient != null) {
                emailNotificationService.sendTaskStatusChangedEmail(
                        recipient.getEmail(),
                        buildFullName(recipient),
                        task.getTitle(),
                        task.getStatus().name(),
                        buildFullName(actor)
                );
            }
        }
    }

    private void notifyTaskCommentParticipants(Task task, Employee commenter, String comment) {
        Set<Long> recipientIds = new java.util.LinkedHashSet<>();
        if (task.getCreatedBy() != null) {
            recipientIds.add(task.getCreatedBy().getId());
        }
        if (task.getAssignee() != null) {
            recipientIds.add(task.getAssignee().getId());
        }
        taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()).stream()
                .map(taskComment -> taskComment.getCommentedBy().getId())
                .forEach(recipientIds::add);

        if (commenter != null) {
            recipientIds.remove(commenter.getId());
        }

        for (Long recipientId : recipientIds) {
            Employee recipient = employeeRepository.findById(recipientId).orElse(null);
            if (recipient == null) {
                continue;
            }
            notificationService.createSystemNotification(
                    recipientId,
                    NotificationType.TASK_COMMENT_ADDED,
                    "New task comment on: " + task.getTitle(),
                    AuditEntityType.TASK.name(),
                    task.getId()
            );
            emailNotificationService.sendTaskCommentAddedEmail(
                    recipient.getEmail(),
                    buildFullName(recipient),
                    task.getTitle(),
                    buildFullName(commenter),
                    comment
            );
        }
    }

    private Employee resolveCurrentEmployeeOrNull() {
        return authorizationService.getCurrentEmployeeOrNull();
    }

    private Long resolveRequestedAssigneeForRead(Long requestedAssigneeId) {
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        if (role != PlatformRole.EMPLOYEE) {
            return requestedAssigneeId;
        }

        return resolveCurrentEmployeeIdOrThrow();
    }

    private void enforceTaskReadAccess(Task task) {
        PlatformRole role = authorizationService.getCurrentRoleOrThrow();
        Long currentEmployeeId = resolveCurrentEmployeeIdOrNull();
        if (!canReadTask(task, role, currentEmployeeId)) {
            throw new ForbiddenOperationException("You are not allowed to access this task");
        }
    }

    private void enforceProjectReadAccess(Project project) {
        if (!authorizationService.canAccessProject(project)) {
            throw new ForbiddenOperationException("You are not allowed to access this project");
        }
    }

    private boolean canReadTask(Task task, PlatformRole role, Long currentEmployeeId) {
        if (role != PlatformRole.EMPLOYEE) {
            return true;
        }
        if (currentEmployeeId == null) {
            return false;
        }
        if (task.getAssignedTeam() == null) {
            return task.getAssignee() != null && currentEmployeeId.equals(task.getAssignee().getId());
        }

        Long assignedTeamId = task.getAssignedTeam().getId();
        if (assignedTeamId == null) {
            return false;
        }

        return isTeamManagerOrLead(currentEmployeeId, assignedTeamId);
    }

    private Long resolveCurrentEmployeeIdOrNull() {
        Employee employee = resolveCurrentEmployeeOrNull();
        return employee == null ? null : employee.getId();
    }

    private Long resolveCurrentEmployeeIdOrThrow() {
        return authorizationService.getCurrentEmployeeIdOrThrow();
    }

    private void enforceTaskCreationAccess(Project project, Long assignedTeamId) {
        if (authorizationService.isTenantAdminEquivalent()) {
            return;
        }
        Long currentEmployeeId = resolveCurrentEmployeeIdOrThrow();
        if (!isTeamManagerOrLead(currentEmployeeId, assignedTeamId)) {
            throw new ForbiddenOperationException("Only project managers or team leads can create tasks for this assigned team");
        }
        if (!projectTeamRepository.existsByProjectIdAndTeamId(project.getId(), assignedTeamId)) {
            throw new ForbiddenOperationException("You can only create tasks for teams assigned to this project");
        }
    }

    private void enforceTaskManagerAccess(Task task) {
        if (authorizationService.isTenantAdminEquivalent()) {
            return;
        }
        Long currentEmployeeId = resolveCurrentEmployeeIdOrThrow();
        Long assignedTeamId = task.getAssignedTeam() == null ? null : task.getAssignedTeam().getId();
        if (assignedTeamId == null || !isTeamManagerOrLead(currentEmployeeId, assignedTeamId)) {
            throw new ForbiddenOperationException("Only project managers or team leads can manage this task");
        }
    }

    private void enforceTaskAssignmentAccess(Task task) {
        if (authorizationService.isTenantAdminEquivalent()) {
            return;
        }
        Long currentEmployeeId = resolveCurrentEmployeeIdOrThrow();
        Long assignedTeamId = task.getAssignedTeam() == null ? null : task.getAssignedTeam().getId();
        if (assignedTeamId == null || !isTeamManagerOrLead(currentEmployeeId, assignedTeamId)) {
            throw new ForbiddenOperationException("Only project managers or team leads can assign tasks");
        }
    }

    private boolean isTeamManagerOrLead(Long employeeId, Long teamId) {
        if (employeeId == null || teamId == null) {
            return false;
        }

        Team team = teamRepository.findById(teamId).orElse(null);
        if (team != null && team.getManager() != null && employeeId.equals(team.getManager().getId())) {
            return true;
        }

        return teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, employeeId)
                .map(teamMember -> teamMember.getFunctionalRole() == TeamFunctionalRole.PROJECT_MANAGER
                        || teamMember.getFunctionalRole() == TeamFunctionalRole.TEAM_LEAD)
                .orElse(false);
    }

    private void validateDueDateRange(LocalDate dueFrom, LocalDate dueTo) {
        if (dueFrom != null && dueTo != null && dueTo.isBefore(dueFrom)) {
            throw new BadRequestException("dueTo cannot be before dueFrom");
        }
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isSortable(String sortBy) {
        return "createdAt".equals(sortBy) ||
                "updatedAt".equals(sortBy) ||
                "dueDate".equals(sortBy) ||
                "priority".equals(sortBy) ||
                "status".equals(sortBy) ||
                "title".equals(sortBy);
    }

    private boolean hasTaskContentChanged(
            String previousTitle,
            String previousDescription,
            LocalDate previousDueDate,
            Task updatedTask) {
        if (!java.util.Objects.equals(previousTitle, updatedTask.getTitle())) {
            return true;
        }
        if (!java.util.Objects.equals(previousDescription, updatedTask.getDescription())) {
            return true;
        }
        return !java.util.Objects.equals(previousDueDate, updatedTask.getDueDate());
    }

    private void notifyTaskUpdated(Task task) {
        Set<Long> recipients = new java.util.LinkedHashSet<>();
        if (task.getCreatedBy() != null) {
            recipients.add(task.getCreatedBy().getId());
        }
        if (task.getAssignee() != null) {
            recipients.add(task.getAssignee().getId());
        }

        Employee actor = resolveCurrentEmployeeOrNull();
        if (actor != null) {
            recipients.remove(actor.getId());
        }

        for (Long recipientId : recipients) {
            notificationService.createSystemNotification(
                    recipientId,
                    NotificationType.TASK_UPDATED,
                    "Task updated: " + task.getTitle(),
                    AuditEntityType.TASK.name(),
                    task.getId()
            );
        }
    }

    private String buildFullName(Employee employee) {
        if (employee == null) {
            return "-";
        }
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? employee.getEmail() : fullName;
    }

    private void publishTaskRealtime(Task task) {
        tenantRealtimePublisher.publishTaskUpdate(
                authorizationService.getCurrentTenantKeyOrThrow(),
                toTaskResponse(task)
        );
    }
}
