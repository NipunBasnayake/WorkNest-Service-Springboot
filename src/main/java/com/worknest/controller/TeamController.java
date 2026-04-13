package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.team.*;
import com.worknest.tenant.service.TeamService;
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
@RequestMapping("/api/tenant/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<TeamResponseDto>> createTeam(@Valid @RequestBody TeamCreateRequestDto requestDto) {
        TeamResponseDto responseDto = teamService.createTeam(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team created successfully", responseDto));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<TeamResponseDto>> updateTeam(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TeamUpdateRequestDto requestDto) {
        TeamResponseDto responseDto = teamService.updateTeam(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Team updated successfully", responseDto));
    }

    @PatchMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<TeamResponseDto>> patchTeam(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TeamUpdateRequestDto requestDto) {
        TeamResponseDto responseDto = teamService.updateTeam(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Team updated successfully", responseDto));
    }

    @PatchMapping("/{id:\\d+}/manager/{managerId:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<TeamResponseDto>> changeManager(
            @PathVariable("id") @Positive Long id,
            @PathVariable("managerId") @Positive Long managerId) {
        TeamResponseDto responseDto = teamService.changeManager(id, managerId);
        return ResponseEntity.ok(ApiResponse.success("Team manager updated successfully", responseDto));
    }

    @PostMapping("/{id:\\d+}/members")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<TeamMemberResponseDto>> addMember(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody TeamMemberAddRequestDto requestDto) {
        TeamMemberResponseDto responseDto = teamService.addMember(id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team member added successfully", responseDto));
    }

    @PatchMapping("/{id:\\d+}/members/{employeeId:\\d+}/functional-role")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<TeamMemberResponseDto>> updateMemberFunctionalRole(
            @PathVariable("id") @Positive Long id,
            @PathVariable("employeeId") @Positive Long employeeId,
            @Valid @RequestBody TeamMemberRoleUpdateRequestDto requestDto) {
        TeamMemberResponseDto responseDto = teamService.updateMemberFunctionalRole(id, employeeId, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Team member functional role updated successfully", responseDto));
    }

    @PatchMapping("/{id:\\d+}/team-members/{teamMemberId:\\d+}/functional-role")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<TeamMemberResponseDto>> updateMemberFunctionalRoleByMemberId(
            @PathVariable("id") @Positive Long id,
            @PathVariable("teamMemberId") @Positive Long teamMemberId,
            @Valid @RequestBody TeamMemberRoleUpdateRequestDto requestDto) {
        TeamMemberResponseDto responseDto = teamService.updateMemberFunctionalRoleByMemberId(id, teamMemberId, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Team member functional role updated successfully", responseDto));
    }

    @DeleteMapping("/{id:\\d+}/members/{employeeId:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable("id") @Positive Long id,
            @PathVariable("employeeId") @Positive Long employeeId) {
        teamService.removeMember(id, employeeId);
        return ResponseEntity.ok(ApiResponse.success("Team member removed successfully"));
    }

    @DeleteMapping("/{id:\\d+}/team-members/{teamMemberId:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<Void>> removeMemberByMemberId(
            @PathVariable("id") @Positive Long id,
            @PathVariable("teamMemberId") @Positive Long teamMemberId) {
        teamService.removeMemberByMemberId(id, teamMemberId);
        return ResponseEntity.ok(ApiResponse.success("Team member removed successfully"));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable("id") @Positive Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.ok(ApiResponse.success("Team deleted successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TeamResponseDto>>> listTeams() {
        List<TeamResponseDto> response = teamService.listTeams();
        return ResponseEntity.ok(ApiResponse.success("Teams retrieved successfully", response));
    }

    @GetMapping(params = {"page", "size"})
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<TeamResponseDto>>> listTeamsPagedByQuery(
            @RequestParam(value = "managerId", required = false) Long managerId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
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

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TeamResponseDto>>> listMyTeams() {
        List<TeamResponseDto> response = teamService.listMyTeams();
        return ResponseEntity.ok(ApiResponse.success("My teams retrieved successfully", response));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<TeamResponseDto>>> listTeamsPaged(
            @RequestParam(value = "managerId", required = false) Long managerId,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
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

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TeamDetailResponseDto>> getTeamDetails(@PathVariable("id") @Positive Long id) {
        TeamDetailResponseDto response = teamService.getTeamDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Team details retrieved successfully", response));
    }

    @GetMapping("/{id:\\d+}/members")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TeamMemberResponseDto>>> listMembers(@PathVariable("id") @Positive Long id) {
        List<TeamMemberResponseDto> response = teamService.listTeamMembers(id);
        return ResponseEntity.ok(ApiResponse.success("Team members retrieved successfully", response));
    }
}
