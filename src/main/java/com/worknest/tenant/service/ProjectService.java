package com.worknest.tenant.service;

import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.project.*;
import com.worknest.tenant.enums.ProjectStatus;

import java.util.List;

public interface ProjectService {

    ProjectResponseDto createProject(ProjectCreateRequestDto requestDto);

    ProjectResponseDto updateProject(Long projectId, ProjectUpdateRequestDto requestDto);

    ProjectResponseDto changeProjectStatus(Long projectId, ProjectStatusUpdateRequestDto requestDto);

    ProjectTeamResponseDto assignTeam(Long projectId, ProjectTeamAssignRequestDto requestDto);

    void removeTeamAssignment(Long projectId, Long teamId);

    void deleteProject(Long projectId);

    List<ProjectResponseDto> listProjects();

    List<ProjectResponseDto> listMyProjects();

    PagedResultDto<ProjectResponseDto> listProjectsPaged(
            ProjectStatus status,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir);

    ProjectDetailResponseDto getProjectDetails(Long projectId);

    List<ProjectTeamResponseDto> listProjectTeams(Long projectId);
}
