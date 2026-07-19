package com.worknest.common.storage;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.master.entity.PlatformUser;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.StoredFileMetadata;
import com.worknest.tenant.repository.AnnouncementRepository;
import com.worknest.tenant.repository.HrMessageRepository;
import com.worknest.tenant.repository.LeaveRequestRepository;
import com.worknest.tenant.repository.ProjectRepository;
import com.worknest.tenant.repository.TaskRepository;
import com.worknest.tenant.repository.TeamChatMessageRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoredFileAccessPolicyAvatarTest {

    private SecurityUtils securityUtils;
    private AuthorizationService authorizationService;
    private StoredFileAccessPolicy policy;

    @BeforeEach
    void setUp() {
        securityUtils = mock(SecurityUtils.class);
        authorizationService = mock(AuthorizationService.class);
        policy = new StoredFileAccessPolicy(
                securityUtils,
                authorizationService,
                mock(TaskRepository.class),
                mock(ProjectRepository.class),
                mock(LeaveRequestRepository.class),
                mock(AnnouncementRepository.class),
                mock(TeamMemberRepository.class),
                mock(TeamChatMessageRepository.class),
                mock(HrMessageRepository.class));
        when(securityUtils.getCurrentPrincipalOrThrow()).thenReturn(principal(100L));
    }

    @Test
    void authenticatedTenantEmployeeCanReadCoworkerAvatar() {
        when(authorizationService.getCurrentEmployeeOrNull()).thenReturn(employee(10L));

        assertThatCode(() -> policy.requireRead(avatarFile(20L, 200L))).doesNotThrowAnyException();
    }

    @Test
    void avatarWriteAllowsSelfOrUploaderButRejectsUnprivilegedCoworker() {
        when(authorizationService.getCurrentEmployeeOrNull()).thenReturn(employee(10L));
        when(authorizationService.hasPermission(com.worknest.security.authorization.Permission.MANAGE_EMPLOYEE))
                .thenReturn(false);

        assertThatCode(() -> policy.requireWrite(avatarFile(10L, 200L))).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireWrite(avatarFile(20L, 100L))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireWrite(avatarFile(20L, 200L)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private StoredFileMetadata avatarFile(Long employeeId, Long uploadedBy) {
        StoredFileMetadata file = new StoredFileMetadata();
        file.setStorageCategory(StorageCategory.EMPLOYEE_AVATAR);
        file.setRelatedEntityId(employeeId);
        file.setUploadedByUserId(uploadedBy);
        return file;
    }

    private Employee employee(Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        return employee;
    }

    private PlatformUserPrincipal principal(Long id) {
        PlatformUser user = new PlatformUser();
        user.setId(id);
        user.setFullName("Employee");
        user.setEmail("employee@example.test");
        user.setPasswordHash("hash");
        user.setRole(PlatformRole.EMPLOYEE);
        user.setStatus(UserStatus.ACTIVE);
        user.setTenantKey("acme");
        return new PlatformUserPrincipal(user);
    }
}
