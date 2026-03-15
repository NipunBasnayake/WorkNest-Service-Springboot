package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.project.*;
import com.worknest.tenant.enums.ProjectStatus;
import com.worknest.tenant.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> createProject(
            @Valid @RequestBody ProjectCreateRequestDto requestDto) {
        ProjectResponseDto responseDto = projectService.createProject(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project created successfully", responseDto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectUpdateRequestDto requestDto) {
        ProjectResponseDto responseDto = projectService.updateProject(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully", responseDto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ProjectResponseDto>> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProjectStatusUpdateRequestDto requestDto) {
        ProjectResponseDto responseDto = projectService.changeProjectStatus(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Project status updated successfully", responseDto));
    }

    @PostMapping("/{id}/teams")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ProjectTeamResponseDto>> assignTeam(
            @PathVariable Long id,
            @Valid @RequestBody ProjectTeamAssignRequestDto requestDto) {
        ProjectTeamResponseDto responseDto = projectService.assignTeam(id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team assigned to project successfully", responseDto));
    }

    @DeleteMapping("/{id}/teams/{teamId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> removeTeamAssignment(
            @PathVariable Long id,
            @PathVariable Long teamId) {
        projectService.removeTeamAssignment(id, teamId);
        return ResponseEntity.ok(ApiResponse.success("Team assignment removed successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<ProjectResponseDto>>> listProjects() {
        List<ProjectResponseDto> response = projectService.listProjects();
        return ResponseEntity.ok(ApiResponse.success("Projects retrieved successfully", response));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<ProjectResponseDto>>> listProjectsPaged(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ProjectDetailResponseDto>> getProjectDetails(@PathVariable Long id) {
        ProjectDetailResponseDto response = projectService.getProjectDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Project details retrieved successfully", response));
    }

    @GetMapping("/{id}/teams")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<ProjectTeamResponseDto>>> listProjectTeams(@PathVariable Long id) {
        List<ProjectTeamResponseDto> response = projectService.listProjectTeams(id);
        return ResponseEntity.ok(ApiResponse.success("Project teams retrieved successfully", response));
    }
}
