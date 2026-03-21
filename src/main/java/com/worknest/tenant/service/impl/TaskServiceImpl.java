package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
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
import com.worknest.tenant.entity.ProjectTeam;
import com.worknest.tenant.entity.Task;
import com.worknest.tenant.entity.TaskComment;
import com.worknest.tenant.enums.AttachmentEntityType;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.enums.ProjectStatus;
import com.worknest.tenant.enums.TaskStatus;
import com.worknest.tenant.repository.AttachmentRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.ProjectRepository;
import com.worknest.tenant.repository.ProjectTeamRepository;
import com.worknest.tenant.repository.TaskCommentRepository;
import com.worknest.tenant.repository.TaskRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import com.worknest.tenant.service.TaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(transactionManager = "transactionManager")
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTeamRepository projectTeamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EmployeeRepository employeeRepository;
    private final AttachmentRepository attachmentRepository;
    private final SecurityUtils securityUtils;
    private final TenantDtoMapper tenantDtoMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EmailNotificationService emailNotificationService;

    public TaskServiceImpl(
            TaskRepository taskRepository,
            TaskCommentRepository taskCommentRepository,
            ProjectRepository projectRepository,
            ProjectTeamRepository projectTeamRepository,
            TeamMemberRepository teamMemberRepository,
            EmployeeRepository employeeRepository,
            AttachmentRepository attachmentRepository,
            SecurityUtils securityUtils,
            TenantDtoMapper tenantDtoMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            EmailNotificationService emailNotificationService) {
        this.taskRepository = taskRepository;
        this.taskCommentRepository = taskCommentRepository;
        this.projectRepository = projectRepository;
        this.projectTeamRepository = projectTeamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.employeeRepository = employeeRepository;
        this.attachmentRepository = attachmentRepository;
        this.securityUtils = securityUtils;
        this.tenantDtoMapper = tenantDtoMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.emailNotificationService = emailNotificationService;
    }

    @Override
    public TaskResponseDto createTask(TaskCreateRequestDto requestDto) {
        Project project = getProjectOrThrow(requestDto.getProjectId());
        ensureProjectAllowsTaskCreation(project);

        Long creatorEmployeeId = resolveActorEmployeeId(requestDto.getCreatedByEmployeeId());
        Employee creator = getActiveEmployeeOrThrow(creatorEmployeeId);
        Employee assignee = resolveAssignee(project.getId(), requestDto.getAssigneeId());

        validateDueDate(project, requestDto.getDueDate());

        Task task = new Task();
        task.setProject(project);
        task.setTitle(requestDto.getTitle().trim());
        task.setDescription(trimToNull(requestDto.getDescription()));
        task.setStatus(requestDto.getStatus());
        task.setPriority(requestDto.getPriority());
        task.setAssignee(assignee);
        task.setCreatedBy(creator);
        task.setDueDate(requestDto.getDueDate());

        Task saved = taskRepository.save(task);
        notifyTaskAssignment(saved);
        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.TASK,
                saved.getId(),
                "{\"title\":\"" + escapeJson(saved.getTitle()) + "\"}"
        );
        return toTaskResponse(saved);
    }

    @Override
    public TaskResponseDto updateTask(Long taskId, TaskUpdateRequestDto requestDto) {
        Task task = getTaskOrThrow(taskId);
        Long previousAssigneeId = task.getAssignee() == null ? null : task.getAssignee().getId();
        TaskStatus previousStatus = task.getStatus();

        if (requestDto.getStatus() != null) {
            validateTaskStatusTransition(task.getStatus(), requestDto.getStatus());
            task.setStatus(requestDto.getStatus());
        }
        if (requestDto.getPriority() != null) {
            task.setPriority(requestDto.getPriority());
        }
        if (requestDto.getAssigneeId() != null) {
            task.setAssignee(resolveAssignee(task.getProject().getId(), requestDto.getAssigneeId()));
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
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"title\":\"" + escapeJson(updated.getTitle()) + "\"}"
        );
        return toTaskResponse(updated);
    }

    @Override
    public TaskResponseDto changeStatus(Long taskId, TaskStatusUpdateRequestDto requestDto) {
        Task task = getTaskOrThrow(taskId);

        PlatformRole currentRole = securityUtils.getCurrentRoleOrThrow();
        if (currentRole == PlatformRole.EMPLOYEE) {
            Long currentEmployeeId = resolveCurrentEmployeeIdOrThrow();
            if (task.getAssignee() == null || !currentEmployeeId.equals(task.getAssignee().getId())) {
                throw new ForbiddenOperationException(
                        "Employees can only update the status of tasks assigned to them");
            }
        }

        validateTaskStatusTransition(task.getStatus(), requestDto.getStatus());
        task.setStatus(requestDto.getStatus());
        Task updated = taskRepository.save(task);
        notifyTaskStatusChange(updated);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"status\":\"" + updated.getStatus() + "\"}"
        );
        return toTaskResponse(updated);
    }

    @Override
    public TaskResponseDto changePriority(Long taskId, TaskPriorityUpdateRequestDto requestDto) {
        Task task = getTaskOrThrow(taskId);
        task.setPriority(requestDto.getPriority());
        Task updated = taskRepository.save(task);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"priority\":\"" + updated.getPriority() + "\"}"
        );
        return toTaskResponse(updated);
    }

    @Override
    public TaskResponseDto changeAssignee(Long taskId, TaskAssigneeUpdateRequestDto requestDto) {
        Task task = getTaskOrThrow(taskId);
        Employee assignee = resolveAssignee(task.getProject().getId(), requestDto.getAssigneeId());
        task.setAssignee(assignee);
        Task updated = taskRepository.save(task);
        notifyTaskAssignment(updated);
        auditLogService.logAction(
                AuditActionType.ASSIGN,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"assigneeId\":" + (assignee == null ? "null" : assignee.getId()) + "}"
        );
        return toTaskResponse(updated);
    }

    @Override
    public TaskResponseDto changeDueDate(Long taskId, TaskDueDateUpdateRequestDto requestDto) {
        Task task = getTaskOrThrow(taskId);
        validateDueDate(task.getProject(), requestDto.getDueDate());
        task.setDueDate(requestDto.getDueDate());
        Task updated = taskRepository.save(task);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TASK,
                updated.getId(),
                "{\"dueDate\":\"" + updated.getDueDate() + "\"}"
        );
        return toTaskResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TaskResponseDto> listMyTasks() {
        Long currentEmployeeId = resolveCurrentEmployeeIdOrThrow();
        return taskRepository.findByAssigneeIdOrderByCreatedAtDesc(currentEmployeeId).stream()
                .map(this::toTaskResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TaskResponseDto> listByProject(Long projectId) {
        getProjectOrThrow(projectId);

        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
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

        return PagedResultDto.<TaskResponseDto>builder()
                .items(resultPage.getContent().stream().map(this::toTaskResponse).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TaskResponseDto> listByAssignee(Long assigneeId) {
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
        getProjectOrThrow(projectId);

        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        Long currentEmployeeId = resolveCurrentEmployeeIdOrNull();

        return taskRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status).stream()
                .filter(task -> canReadTask(task, role, currentEmployeeId))
                .map(this::toTaskResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public TaskResponseDto getTaskById(Long taskId) {
        Task task = getTaskOrThrow(taskId);
        enforceTaskReadAccess(task);
        return toTaskResponse(task);
    }

    @Override
    public void deleteTask(Long taskId) {
        Task task = getTaskOrThrow(taskId);

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
        Task task = getTaskOrThrow(taskId);
        enforceTaskReadAccess(task);

        Long commenterEmployeeId = resolveActorEmployeeId(requestDto.getCommentedByEmployeeId());
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
        Task task = getTaskOrThrow(taskId);
        enforceTaskReadAccess(task);
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toTaskCommentResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public KanbanBoardResponseDto getKanbanBoard(Long projectId) {
        getProjectOrThrow(projectId);
        enforceProjectReadAccess(projectId);

        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
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

    private Employee resolveAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }

        Employee assignee = getActiveEmployeeOrThrow(assigneeId);
        if (!isEmployeeAssignableToProject(projectId, assigneeId)) {
            throw new BadRequestException("Employee is not a member of any team assigned to this project");
        }
        return assignee;
    }

    private boolean isEmployeeAssignableToProject(Long projectId, Long employeeId) {
        List<ProjectTeam> assignedTeams = projectTeamRepository.findByProjectId(projectId);
        if (assignedTeams.isEmpty()) {
            return true;
        }

        Set<Long> projectTeamIds = assignedTeams.stream()
                .map(projectTeam -> projectTeam.getTeam().getId())
                .collect(Collectors.toSet());

        boolean isAssignedTeamManager = assignedTeams.stream()
                .map(ProjectTeam::getTeam)
                .anyMatch(team -> team.getManager() != null && team.getManager().getId().equals(employeeId));
        if (isAssignedTeamManager) {
            return true;
        }

        return teamMemberRepository.findByEmployeeIdAndLeftAtIsNull(employeeId).stream()
                .map(teamMember -> teamMember.getTeam().getId())
                .anyMatch(projectTeamIds::contains);
    }

    private void validateDueDate(Project project, LocalDate dueDate) {
        if (dueDate == null) {
            return;
        }

        if (project.getStartDate() != null && dueDate.isBefore(project.getStartDate())) {
            throw new BadRequestException("Task due date cannot be before project start date");
        }
        if (project.getEndDate() != null && dueDate.isAfter(project.getEndDate())) {
            throw new BadRequestException("Task due date cannot be after project end date");
        }
    }

    private void validateTaskStatusTransition(TaskStatus currentStatus, TaskStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            return;
        }

        if (currentStatus == TaskStatus.DONE && newStatus != TaskStatus.DONE) {
            throw new BadRequestException("Completed task cannot be moved back to another status");
        }
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
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .assignee(tenantDtoMapper.toEmployeeSimple(task.getAssignee()))
                .createdBy(tenantDtoMapper.toEmployeeSimple(task.getCreatedBy()))
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

    private Long resolveActorEmployeeId(Long requestedActorEmployeeId) {
        if (requestedActorEmployeeId != null) {
            validateActorEmployeeId(requestedActorEmployeeId);
            return requestedActorEmployeeId;
        }

        Employee currentEmployee = resolveCurrentEmployeeOrNull();
        if (currentEmployee == null) {
            throw new ForbiddenOperationException(
                    "Current user is not linked to an employee profile. Actor employee ID is required");
        }
        return currentEmployee.getId();
    }

    private void validateActorEmployeeId(Long actorEmployeeId) {
        Employee currentEmployee = resolveCurrentEmployeeOrNull();
        if (currentEmployee != null) {
            if (!currentEmployee.getId().equals(actorEmployeeId)) {
                throw new ForbiddenOperationException("Authenticated user cannot act on behalf of another employee");
            }
            return;
        }

        if (securityUtils.getCurrentRoleOrThrow() != PlatformRole.TENANT_ADMIN) {
            throw new ForbiddenOperationException("Current user is not linked to an employee profile");
        }
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

        if (task.getCreatedBy().getId().equals(task.getAssignee().getId())) {
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
                    NotificationType.TASK_STATUS_CHANGED,
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
        PlatformUserPrincipal principal = securityUtils.getCurrentPrincipalOrThrow();

        if (principal.getId() != null) {
            Employee employeeByPlatformId = employeeRepository.findByPlatformUserId(principal.getId()).orElse(null);
            if (employeeByPlatformId != null) {
                return employeeByPlatformId;
            }
        }

        String currentEmail = principal.getEmail();
        if (currentEmail == null || currentEmail.isBlank()) {
            return null;
        }

        return employeeRepository.findByEmailIgnoreCase(currentEmail).orElse(null);
    }

    private Long resolveRequestedAssigneeForRead(Long requestedAssigneeId) {
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        if (role != PlatformRole.EMPLOYEE) {
            return requestedAssigneeId;
        }

        return resolveCurrentEmployeeIdOrThrow();
    }

    private void enforceTaskReadAccess(Task task) {
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        if (role != PlatformRole.EMPLOYEE) {
            return;
        }

        Long currentEmployeeId = resolveCurrentEmployeeIdOrThrow();
        if (!canReadTask(task, role, currentEmployeeId)) {
            throw new ForbiddenOperationException("Employees can access only tasks they created or are assigned to");
        }
    }

    private void enforceProjectReadAccess(Long projectId) {
        PlatformRole role = securityUtils.getCurrentRoleOrThrow();
        if (role != PlatformRole.EMPLOYEE) {
            return;
        }

        Long currentEmployeeId = resolveCurrentEmployeeIdOrThrow();
        boolean canAccess = taskRepository.existsByProjectIdAndParticipantEmployeeId(projectId, currentEmployeeId);
        if (!canAccess) {
            throw new ForbiddenOperationException("Employees are not allowed to access this project's board");
        }
    }

    private boolean canReadTask(Task task, PlatformRole role, Long currentEmployeeId) {
        if (role != PlatformRole.EMPLOYEE) {
            return true;
        }
        if (currentEmployeeId == null) {
            return false;
        }
        if (task.getCreatedBy() != null && currentEmployeeId.equals(task.getCreatedBy().getId())) {
            return true;
        }
        return task.getAssignee() != null && currentEmployeeId.equals(task.getAssignee().getId());
    }

    private Long resolveCurrentEmployeeIdOrNull() {
        Employee employee = resolveCurrentEmployeeOrNull();
        return employee == null ? null : employee.getId();
    }

    private Long resolveCurrentEmployeeIdOrThrow() {
        Employee employee = resolveCurrentEmployeeOrNull();
        if (employee == null) {
            throw new ForbiddenOperationException("Current user is not linked to an employee profile");
        }
        return employee.getId();
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

    private String buildFullName(Employee employee) {
        if (employee == null) {
            return "-";
        }
        String firstName = employee.getFirstName() == null ? "" : employee.getFirstName().trim();
        String lastName = employee.getLastName() == null ? "" : employee.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? employee.getEmail() : fullName;
    }
}
