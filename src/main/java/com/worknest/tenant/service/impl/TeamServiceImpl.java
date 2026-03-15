package com.worknest.tenant.service.impl;

import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.team.*;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.entity.TeamMember;
import com.worknest.tenant.enums.AuditActionType;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.TeamFunctionalRole;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.TeamService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(transactionManager = "transactionManager")
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantDtoMapper tenantDtoMapper;
    private final AuditLogService auditLogService;

    public TeamServiceImpl(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            EmployeeRepository employeeRepository,
            TenantDtoMapper tenantDtoMapper,
            AuditLogService auditLogService) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.employeeRepository = employeeRepository;
        this.tenantDtoMapper = tenantDtoMapper;
        this.auditLogService = auditLogService;
    }

    @Override
    public TeamResponseDto createTeam(TeamCreateRequestDto requestDto) {
        String teamName = normalizeTeamName(requestDto.getName());
        if (teamRepository.existsByNameIgnoreCase(teamName)) {
            throw new BadRequestException("Team name already exists: " + teamName);
        }

        Employee manager = getActiveEmployeeOrThrow(requestDto.getManagerId());

        Team team = new Team();
        team.setName(teamName);
        team.setManager(manager);

        Team saved = teamRepository.save(team);
        auditLogService.logAction(
                AuditActionType.CREATE,
                AuditEntityType.TEAM,
                saved.getId(),
                "{\"name\":\"" + escapeJson(saved.getName()) + "\"}"
        );
        return toTeamResponse(saved);
    }

    @Override
    public TeamResponseDto updateTeam(Long teamId, TeamUpdateRequestDto requestDto) {
        Team team = getTeamOrThrow(teamId);
        String teamName = normalizeTeamName(requestDto.getName());

        if (!team.getName().equalsIgnoreCase(teamName) && teamRepository.existsByNameIgnoreCase(teamName)) {
            throw new BadRequestException("Team name already exists: " + teamName);
        }

        Employee manager = getActiveEmployeeOrThrow(requestDto.getManagerId());

        team.setName(teamName);
        team.setManager(manager);

        Team updated = teamRepository.save(team);
        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TEAM,
                updated.getId(),
                "{\"name\":\"" + escapeJson(updated.getName()) + "\"}"
        );
        return toTeamResponse(updated);
    }

    @Override
    public TeamResponseDto changeManager(Long teamId, Long managerEmployeeId) {
        Team team = getTeamOrThrow(teamId);
        Employee manager = getActiveEmployeeOrThrow(managerEmployeeId);
        team.setManager(manager);
        Team updated = teamRepository.save(team);
        auditLogService.logAction(
                AuditActionType.ASSIGN,
                AuditEntityType.TEAM,
                updated.getId(),
                "{\"managerId\":" + manager.getId() + "}"
        );
        return toTeamResponse(updated);
    }

    @Override
    public TeamMemberResponseDto addMember(Long teamId, TeamMemberAddRequestDto requestDto) {
        Team team = getTeamOrThrow(teamId);
        Employee employee = getActiveEmployeeOrThrow(requestDto.getEmployeeId());

        if (teamMemberRepository.findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, employee.getId()).isPresent()) {
            throw new BadRequestException("Employee is already an active member of this team");
        }

        TeamMember teamMember = new TeamMember();
        teamMember.setTeam(team);
        teamMember.setEmployee(employee);
        teamMember.setFunctionalRole(
                requestDto.getFunctionalRole() == null ? TeamFunctionalRole.MEMBER : requestDto.getFunctionalRole());
        teamMember.setJoinedAt(LocalDateTime.now());

        TeamMember savedMember = teamMemberRepository.save(teamMember);
        auditLogService.logAction(
                AuditActionType.ASSIGN,
                AuditEntityType.TEAM,
                teamId,
                "{\"employeeId\":" + employee.getId() + ",\"functionalRole\":\"" + savedMember.getFunctionalRole() + "\"}"
        );
        return toTeamMemberResponse(savedMember);
    }

    @Override
    public TeamMemberResponseDto updateMemberFunctionalRole(
            Long teamId,
            Long employeeId,
            TeamMemberRoleUpdateRequestDto requestDto) {
        getTeamOrThrow(teamId);

        TeamMember teamMember = teamMemberRepository
                .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Active team membership not found"));

        teamMember.setFunctionalRole(requestDto.getFunctionalRole());
        TeamMember updated = teamMemberRepository.save(teamMember);

        auditLogService.logAction(
                AuditActionType.UPDATE,
                AuditEntityType.TEAM,
                teamId,
                "{\"employeeId\":" + employeeId + ",\"functionalRole\":\"" + updated.getFunctionalRole() + "\"}"
        );
        return toTeamMemberResponse(updated);
    }

    @Override
    public void removeMember(Long teamId, Long employeeId) {
        TeamMember teamMember = teamMemberRepository
                .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(teamId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Active team membership not found"));

        teamMember.setLeftAt(LocalDateTime.now());
        teamMemberRepository.save(teamMember);
        auditLogService.logAction(
                AuditActionType.DELETE,
                AuditEntityType.TEAM,
                teamId,
                "{\"employeeId\":" + employeeId + "}"
        );
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TeamResponseDto> listTeams() {
        return teamRepository.findAll().stream()
                .map(this::toTeamResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public TeamDetailResponseDto getTeamDetails(Long teamId) {
        Team team = getTeamOrThrow(teamId);
        List<TeamMemberResponseDto> members = listTeamMembers(teamId);

        return TeamDetailResponseDto.builder()
                .team(toTeamResponse(team))
                .members(members)
                .build();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<TeamMemberResponseDto> listTeamMembers(Long teamId) {
        getTeamOrThrow(teamId);
        return teamMemberRepository.findByTeamIdOrderByJoinedAtDesc(teamId)
                .stream()
                .map(this::toTeamMemberResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public PagedResultDto<TeamResponseDto> listTeamsPaged(
            Long managerId,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDir) {
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(Math.min(size, 100), 1);
        String resolvedSortBy = isSortable(sortBy) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Page<Team> resultPage = teamRepository.search(
                managerId,
                trimToNull(search),
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(direction, resolvedSortBy))
        );

        return PagedResultDto.<TeamResponseDto>builder()
                .items(resultPage.getContent().stream().map(this::toTeamResponse).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .build();
    }

    private Team getTeamOrThrow(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId));
    }

    private Employee getActiveEmployeeOrThrow(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (employee.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Employee is not active: " + employeeId);
        }
        return employee;
    }

    private TeamResponseDto toTeamResponse(Team team) {
        long activeMembers = teamMemberRepository.findByTeamIdOrderByJoinedAtDesc(team.getId())
                .stream()
                .filter(member -> member.getLeftAt() == null)
                .count();

        return TeamResponseDto.builder()
                .id(team.getId())
                .name(team.getName())
                .manager(tenantDtoMapper.toEmployeeSimple(team.getManager()))
                .activeMemberCount(activeMembers)
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
    }

    private TeamMemberResponseDto toTeamMemberResponse(TeamMember teamMember) {
        return TeamMemberResponseDto.builder()
                .id(teamMember.getId())
                .teamId(teamMember.getTeam().getId())
                .employee(tenantDtoMapper.toEmployeeSimple(teamMember.getEmployee()))
                .functionalRole(teamMember.getFunctionalRole())
                .joinedAt(teamMember.getJoinedAt())
                .leftAt(teamMember.getLeftAt())
                .build();
    }

    private String normalizeTeamName(String name) {
        String normalized = name == null ? null : name.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Team name is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isSortable(String sortBy) {
        return "createdAt".equals(sortBy) || "updatedAt".equals(sortBy) || "name".equals(sortBy);
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
