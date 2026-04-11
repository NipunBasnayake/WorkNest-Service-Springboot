package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.task.*;
import com.worknest.tenant.enums.TaskStatus;
import com.worknest.tenant.service.TaskService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/tenant/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskResponseDto>> createTask(
            @Valid @RequestBody TaskCreateRequestDto requestDto) {
        TaskResponseDto response = taskService.createTask(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Task created successfully", response));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskResponseDto>> updateTask(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TaskUpdateRequestDto requestDto) {
        TaskResponseDto response = taskService.updateTask(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Task updated successfully", response));
    }

    @PatchMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskResponseDto>> patchTask(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TaskUpdateRequestDto requestDto) {
        TaskResponseDto response = taskService.updateTask(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Task updated successfully", response));
    }

    @PatchMapping("/{id:\\d+}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskResponseDto>> changeStatus(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TaskStatusUpdateRequestDto requestDto) {
        TaskResponseDto response = taskService.changeStatus(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Task status updated successfully", response));
    }

    @PatchMapping("/{id:\\d+}/priority")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskResponseDto>> changePriority(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TaskPriorityUpdateRequestDto requestDto) {
        TaskResponseDto response = taskService.changePriority(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Task priority updated successfully", response));
    }

    @PatchMapping("/{id:\\d+}/assignee")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskResponseDto>> changeAssignee(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TaskAssigneeUpdateRequestDto requestDto) {
        TaskResponseDto response = taskService.changeAssignee(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Task assignee updated successfully", response));
    }

    @PatchMapping("/{id:\\d+}/due-date")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskResponseDto>> changeDueDate(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TaskDueDateUpdateRequestDto requestDto) {
        TaskResponseDto response = taskService.changeDueDate(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Task due date updated successfully", response));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable("id") @Positive Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok(ApiResponse.success("Task deleted successfully"));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<TaskResponseDto>>> listTasksPaged(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "status", required = false) TaskStatus status,
            @RequestParam(value = "assigneeId", required = false) Long assigneeId,
            @RequestParam(value = "dueFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(value = "dueTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        PagedResultDto<TaskResponseDto> response = taskService.listTasksPaged(
                projectId,
                status,
                assigneeId,
                dueFrom,
                dueTo,
                search,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully", response));
    }

    @GetMapping(params = {"page", "size"})
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<TaskResponseDto>>> listTasks(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "status", required = false) TaskStatus status,
            @RequestParam(value = "assigneeId", required = false) Long assigneeId,
            @RequestParam(value = "dueFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(value = "dueTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        PagedResultDto<TaskResponseDto> response = taskService.listTasksPaged(
                projectId,
                status,
                assigneeId,
                dueFrom,
                dueTo,
                search,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Tasks retrieved successfully", response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> listMyTasks() {
        List<TaskResponseDto> response = taskService.listMyTasks();
        return ResponseEntity.ok(ApiResponse.success("My tasks retrieved successfully", response));
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskResponseDto>> getTaskById(@PathVariable("id") @Positive Long id) {
        TaskResponseDto response = taskService.getTaskById(id);
        return ResponseEntity.ok(ApiResponse.success("Task retrieved successfully", response));
    }

    @GetMapping("/project/{projectId:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> listByProject(@PathVariable("projectId") @Positive Long projectId) {
        List<TaskResponseDto> response = taskService.listByProject(projectId);
        return ResponseEntity.ok(ApiResponse.success("Project tasks retrieved successfully", response));
    }

    @GetMapping("/assignee/{assigneeId:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> listByAssignee(@PathVariable("assigneeId") @Positive Long assigneeId) {
        List<TaskResponseDto> response = taskService.listByAssignee(assigneeId);
        return ResponseEntity.ok(ApiResponse.success("Assignee tasks retrieved successfully", response));
    }

    @GetMapping("/project/{projectId:\\d+}/status/{status}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> listByProjectAndStatus(
            @PathVariable("projectId") @Positive Long projectId,
            @PathVariable("status") TaskStatus status) {
        List<TaskResponseDto> response = taskService.listByProjectAndStatus(projectId, status);
        return ResponseEntity.ok(ApiResponse.success("Project tasks by status retrieved successfully", response));
    }

    @PostMapping("/{id:\\d+}/comments")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskCommentResponseDto>> addComment(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TaskCommentCreateRequestDto requestDto) {
        TaskCommentResponseDto response = taskService.addComment(id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Task comment added successfully", response));
    }

    @GetMapping("/{id:\\d+}/comments")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TaskCommentResponseDto>>> listComments(@PathVariable("id") @Positive Long id) {
        List<TaskCommentResponseDto> response = taskService.listComments(id);
        return ResponseEntity.ok(ApiResponse.success("Task comments retrieved successfully", response));
    }

    @GetMapping("/project/{projectId:\\d+}/kanban")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<KanbanBoardResponseDto>> getKanbanBoard(@PathVariable("projectId") @Positive Long projectId) {
        KanbanBoardResponseDto response = taskService.getKanbanBoard(projectId);
        return ResponseEntity.ok(ApiResponse.success("Kanban board data retrieved successfully", response));
    }
}
