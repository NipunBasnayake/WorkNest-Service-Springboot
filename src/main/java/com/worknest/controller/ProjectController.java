package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.project.*;
import com.worknest.tenant.dto.task.TaskCreateRequestDto;
import com.worknest.tenant.dto.task.TaskResponseDto;
import com.worknest.tenant.enums.ProjectStatus;
import com.worknest.tenant.service.ProjectService;
import com.worknest.tenant.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/tenant/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final TaskService taskService;

    public ProjectController(ProjectService projectService, TaskService taskService) {
        this.projectService = projectService;
        this.taskService = taskService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> createProject(
            @Valid @RequestBody ProjectCreateRequestDto requestDto) {
        ProjectResponseDto responseDto = projectService.createProject(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project created successfully", responseDto));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> updateProject(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody ProjectUpdateRequestDto requestDto) {
        ProjectResponseDto responseDto = projectService.updateProject(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully", responseDto));
    }

    @PatchMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> patchProject(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody ProjectUpdateRequestDto requestDto) {
        ProjectResponseDto responseDto = projectService.updateProject(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully", responseDto));
    }

    @PatchMapping("/{id:\\d+}/status")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> changeStatus(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody ProjectStatusUpdateRequestDto requestDto) {
        ProjectResponseDto responseDto = projectService.changeProjectStatus(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Project status updated successfully", responseDto));
    }

    @PostMapping("/{id:\\d+}/teams")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ProjectTeamResponseDto>> assignTeam(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody ProjectTeamAssignRequestDto requestDto) {
        ProjectTeamResponseDto responseDto = projectService.assignTeam(id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team assigned to project successfully", responseDto));
    }

    @DeleteMapping("/{id:\\d+}/teams/{teamId:\\d+}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeTeamAssignment(
            @PathVariable("id") @Positive Long id,
            @PathVariable("teamId") @Positive Long teamId) {
        projectService.removeTeamAssignment(id, teamId);
        return ResponseEntity.ok(ApiResponse.success("Team assignment removed successfully"));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable("id") @Positive Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.ok(ApiResponse.success("Project deleted successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<ProjectResponseDto>>> listProjects() {
        List<ProjectResponseDto> response = projectService.listProjects();
        return ResponseEntity.ok(ApiResponse.success("Projects retrieved successfully", response));
    }

    @GetMapping(params = {"page", "size"})
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<ProjectResponseDto>>> listProjectsPagedByQuery(
            @RequestParam(value = "status", required = false) ProjectStatus status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        PagedResultDto<ProjectResponseDto> response = projectService.listProjectsPaged(
                status,
                search,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Projects retrieved successfully", response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<ProjectResponseDto>>> listMyProjects() {
        List<ProjectResponseDto> response = projectService.listMyProjects();
        return ResponseEntity.ok(ApiResponse.success("My projects retrieved successfully", response));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<ProjectResponseDto>>> listProjectsPaged(
            @RequestParam(value = "status", required = false) ProjectStatus status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        PagedResultDto<ProjectResponseDto> response = projectService.listProjectsPaged(
                status,
                search,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Projects retrieved successfully", response));
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ProjectDetailResponseDto>> getProjectDetails(@PathVariable("id") @Positive Long id) {
        ProjectDetailResponseDto response = projectService.getProjectDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Project details retrieved successfully", response));
    }

    @GetMapping("/{id:\\d+}/teams")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<ProjectTeamResponseDto>>> listProjectTeams(@PathVariable("id") @Positive Long id) {
        List<ProjectTeamResponseDto> response = projectService.listProjectTeams(id);
        return ResponseEntity.ok(ApiResponse.success("Project teams retrieved successfully", response));
    }

    @PostMapping("/{id:\\d+}/tasks")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TaskResponseDto>> createProjectTask(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TaskCreateRequestDto requestDto) {
        requestDto.setProjectId(id);
        TaskResponseDto response = taskService.createTask(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project task created successfully", response));
    }

    @GetMapping("/{id:\\d+}/tasks")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> listProjectTasks(@PathVariable("id") @Positive Long id) {
        List<TaskResponseDto> response = taskService.listByProject(id);
        return ResponseEntity.ok(ApiResponse.success("Project tasks retrieved successfully", response));
    }
}
