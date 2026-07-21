package com.worknest.security.authorization;

import com.worknest.security.service.CurrentUserService;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.enums.TeamFunctionalRole;
import com.worknest.tenant.repository.ProjectTeamRepository;
import com.worknest.tenant.repository.TaskRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceImplTest {

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private RolePermissionMatrix rolePermissionMatrix;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private ProjectTeamRepository projectTeamRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private AuthorizationServiceImpl authorizationService;

    @Test
    void checksProjectTeamRolesWithOneRepositoryQuery() {
        EnumSet<TeamFunctionalRole> roles = EnumSet.of(
                TeamFunctionalRole.PROJECT_MANAGER,
                TeamFunctionalRole.TEAM_LEAD);
        when(currentUserService.getCurrentEmployeeIdOrNull()).thenReturn(42L);
        when(teamMemberRepository.existsActiveProjectRole(7L, 42L, roles)).thenReturn(true);

        boolean authorized = authorizationService.hasAnyTeamRoleForProject(
                7L,
                TeamFunctionalRole.PROJECT_MANAGER,
                TeamFunctionalRole.TEAM_LEAD);

        assertThat(authorized).isTrue();
        verify(teamMemberRepository).existsActiveProjectRole(7L, 42L, roles);
        verifyNoInteractions(projectTeamRepository);
    }
}
