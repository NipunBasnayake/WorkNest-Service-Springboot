package com.worknest.tenant.service;

import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.team.*;

import java.util.List;

public interface TeamService {

    TeamResponseDto createTeam(TeamCreateRequestDto requestDto);

    TeamResponseDto updateTeam(Long teamId, TeamUpdateRequestDto requestDto);

    TeamResponseDto changeManager(Long teamId, Long managerEmployeeId);

    TeamMemberResponseDto addMember(Long teamId, TeamMemberAddRequestDto requestDto);

    TeamMemberResponseDto updateMemberFunctionalRole(Long teamId, Long employeeId, TeamMemberRoleUpdateRequestDto requestDto);

    void removeMember(Long teamId, Long employeeId);

    void deleteTeam(Long teamId);

    List<TeamResponseDto> listTeams();

    List<TeamResponseDto> listMyTeams();

    PagedResultDto<TeamResponseDto> listTeamsPaged(
            Long managerId,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir);

    TeamDetailResponseDto getTeamDetails(Long teamId);

    List<TeamMemberResponseDto> listTeamMembers(Long teamId);
}
