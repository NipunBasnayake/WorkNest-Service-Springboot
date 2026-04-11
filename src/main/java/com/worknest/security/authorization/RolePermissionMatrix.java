package com.worknest.security.authorization;

import com.worknest.common.enums.PlatformRole;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class RolePermissionMatrix {

    private final Map<PlatformRole, Set<Permission>> matrix = new EnumMap<>(PlatformRole.class);

    public RolePermissionMatrix() {
        Set<Permission> tenantAdminPermissions = EnumSet.of(
                Permission.MANAGE_TENANT_SETTINGS,
                Permission.CREATE_EMPLOYEE,
                Permission.MANAGE_EMPLOYEE,
                Permission.VIEW_EMPLOYEE,
                Permission.CREATE_TEAM,
                Permission.MANAGE_TEAM,
                Permission.ASSIGN_TEAM_ROLE,
                Permission.VIEW_TEAM,
                Permission.CREATE_PROJECT,
                Permission.MANAGE_PROJECT,
                Permission.VIEW_PROJECT,
                Permission.CREATE_TASK,
                Permission.MANAGE_TASK,
                Permission.ASSIGN_TASK,
                Permission.UPDATE_TASK_STATUS,
                Permission.VIEW_TASK,
                Permission.VIEW_ATTENDANCE,
                Permission.VIEW_ALL_ATTENDANCE,
                Permission.CHECK_IN_OUT,
                Permission.APPLY_LEAVE,
                Permission.VIEW_LEAVE,
                Permission.APPROVE_LEAVE,
                Permission.VIEW_SELF_DATA,
                Permission.MANAGE_SELF_PROFILE,
                Permission.CHAT_ACCESS,
                Permission.SEND_ANNOUNCEMENT,
                Permission.SEND_NOTIFICATION
        );

        Set<Permission> hrPermissions = EnumSet.of(
                Permission.CREATE_EMPLOYEE,
                Permission.MANAGE_EMPLOYEE,
                Permission.VIEW_EMPLOYEE,
                Permission.CREATE_TEAM,
                Permission.MANAGE_TEAM,
                Permission.ASSIGN_TEAM_ROLE,
                Permission.VIEW_TEAM,
                Permission.CREATE_TASK,
                Permission.VIEW_PROJECT,
                Permission.MANAGE_TASK,
                Permission.ASSIGN_TASK,
                Permission.UPDATE_TASK_STATUS,
                Permission.VIEW_TASK,
                Permission.VIEW_ATTENDANCE,
                Permission.VIEW_ALL_ATTENDANCE,
                Permission.CHECK_IN_OUT,
                Permission.APPLY_LEAVE,
                Permission.VIEW_LEAVE,
                Permission.APPROVE_LEAVE,
                Permission.VIEW_SELF_DATA,
                Permission.MANAGE_SELF_PROFILE,
                Permission.CHAT_ACCESS,
                Permission.SEND_ANNOUNCEMENT
        );

        Set<Permission> employeePermissions = EnumSet.of(
                Permission.VIEW_TEAM,
                Permission.VIEW_PROJECT,
                Permission.VIEW_TASK,
                Permission.UPDATE_TASK_STATUS,
                Permission.CHECK_IN_OUT,
                Permission.APPLY_LEAVE,
                Permission.VIEW_LEAVE,
                Permission.VIEW_ATTENDANCE,
                Permission.VIEW_SELF_DATA,
                Permission.MANAGE_SELF_PROFILE,
                Permission.CHAT_ACCESS
        );

        Set<Permission> legacyManagerPermissions = EnumSet.of(
                Permission.VIEW_EMPLOYEE,
                Permission.VIEW_TEAM,
                Permission.CREATE_PROJECT,
                Permission.MANAGE_PROJECT,
                Permission.VIEW_PROJECT,
                Permission.CREATE_TASK,
                Permission.MANAGE_TASK,
                Permission.ASSIGN_TASK,
                Permission.UPDATE_TASK_STATUS,
                Permission.VIEW_TASK,
                Permission.VIEW_ATTENDANCE,
                Permission.VIEW_ALL_ATTENDANCE,
                Permission.CHECK_IN_OUT,
                Permission.APPLY_LEAVE,
                Permission.VIEW_LEAVE,
                Permission.VIEW_SELF_DATA,
                Permission.MANAGE_SELF_PROFILE,
                Permission.CHAT_ACCESS,
                Permission.SEND_NOTIFICATION
        );

        matrix.put(PlatformRole.PLATFORM_ADMIN, EnumSet.of(
                Permission.MANAGE_TENANT,
                Permission.SEND_PLATFORM_ANNOUNCEMENT
        ));
        matrix.put(PlatformRole.TENANT_ADMIN, tenantAdminPermissions);
        matrix.put(PlatformRole.ADMIN, EnumSet.copyOf(tenantAdminPermissions));
        matrix.put(PlatformRole.HR, hrPermissions);
        matrix.put(PlatformRole.EMPLOYEE, employeePermissions);
        matrix.put(PlatformRole.MANAGER, legacyManagerPermissions);
    }

    public boolean hasPermission(PlatformRole role, Permission permission) {
        if (role == null || permission == null) {
            return false;
        }
        return matrix.getOrDefault(role, Set.of()).contains(permission);
    }

    public Set<Permission> getPermissions(PlatformRole role) {
        if (role == null) {
            return Set.of();
        }
        return Set.copyOf(matrix.getOrDefault(role, Set.of()));
    }
}
