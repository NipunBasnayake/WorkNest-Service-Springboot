package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.team.*;
import com.worknest.tenant.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TeamResponseDto>> createTeam(@Valid @RequestBody TeamCreateRequestDto requestDto) {
        TeamResponseDto responseDto = teamService.createTeam(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team created successfully", responseDto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TeamResponseDto>> updateTeam(
            @PathVariable Long id,
            @Valid @RequestBody TeamUpdateRequestDto requestDto) {
        TeamResponseDto responseDto = teamService.updateTeam(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Team updated successfully", responseDto));
    }

    @PatchMapping("/{id}/manager/{managerId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TeamResponseDto>> changeManager(
            @PathVariable Long id,
            @PathVariable Long managerId) {
        TeamResponseDto responseDto = teamService.changeManager(id, managerId);
        return ResponseEntity.ok(ApiResponse.success("Team manager updated successfully", responseDto));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TeamMemberResponseDto>> addMember(
            @PathVariable Long id,
            @Valid @RequestBody TeamMemberAddRequestDto requestDto) {
        TeamMemberResponseDto responseDto = teamService.addMember(id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team member added successfully", responseDto));
    }

    @PatchMapping("/{id}/members/{employeeId}/functional-role")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<TeamMemberResponseDto>> updateMemberFunctionalRole(
            @PathVariable Long id,
            @PathVariable Long employeeId,
            @Valid @RequestBody TeamMemberRoleUpdateRequestDto requestDto) {
        TeamMemberResponseDto responseDto = teamService.updateMemberFunctionalRole(id, employeeId, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Team member functional role updated successfully", responseDto));
    }

    @DeleteMapping("/{id}/members/{employeeId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long id,
            @PathVariable Long employeeId) {
        teamService.removeMember(id, employeeId);
        return ResponseEntity.ok(ApiResponse.success("Team member removed successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.ok(ApiResponse.success("Team deleted successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TeamResponseDto>>> listTeams() {
        List<TeamResponseDto> response = teamService.listTeams();
        return ResponseEntity.ok(ApiResponse.success("Teams retrieved successfully", response));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<TeamResponseDto>>> listTeamsPaged(
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PagedResultDto<TeamResponseDto> response = teamService.listTeamsPaged(
                managerId,
                search,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Teams retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TeamDetailResponseDto>> getTeamDetails(@PathVariable Long id) {
        TeamDetailResponseDto response = teamService.getTeamDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Team details retrieved successfully", response));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TeamMemberResponseDto>>> listMembers(@PathVariable Long id) {
        List<TeamMemberResponseDto> response = teamService.listTeamMembers(id);
        return ResponseEntity.ok(ApiResponse.success("Team members retrieved successfully", response));
    }
}
