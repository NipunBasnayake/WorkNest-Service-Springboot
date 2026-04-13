package com.worknest.security.authorization;

import com.worknest.common.enums.PlatformRole;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Project;
import com.worknest.tenant.entity.Task;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.enums.TeamFunctionalRole;

public interface AuthorizationService {

    boolean hasPermission(Permission permission);

    void requirePermission(Permission permission);

    boolean isTaskAssignee(Task task);

    boolean isTeamMember(Long teamId);

    boolean hasTeamRole(Long teamId, TeamFunctionalRole... roles);

    Employee getCurrentEmployeeOrThrow();

    Employee getCurrentEmployeeOrNull();

    Long getCurrentEmployeeIdOrThrow();

    Long getCurrentEmployeeIdOrNull();

    Long getCurrentUserIdOrThrow();

    String getCurrentTenantKeyOrThrow();

    PlatformRole getCurrentRoleOrThrow();

    boolean isTenantAdminEquivalent();

    boolean hasAnyTeamRoleForProject(Long projectId, TeamFunctionalRole... roles);

    boolean canAccessProject(Project project);

    boolean canAccessTask(Task task);

    boolean canAccessTeam(Team team);
}
