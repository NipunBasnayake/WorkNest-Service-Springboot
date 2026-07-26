package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.common.exception.ForbiddenOperationException;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.security.model.PlatformUserPrincipal;
import com.worknest.security.util.SecurityUtils;
import com.worknest.tenant.dto.announcement.AnnouncementCreateRequestDto;
import com.worknest.tenant.dto.announcement.AnnouncementResponseDto;
import com.worknest.tenant.dto.announcement.AnnouncementUpdateRequestDto;
import com.worknest.tenant.entity.Announcement;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Team;
import com.worknest.tenant.enums.AttachmentEntityType;
import com.worknest.tenant.repository.AnnouncementRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
import com.worknest.tenant.service.AttachmentService;
import com.worknest.tenant.service.AuditLogService;
import com.worknest.tenant.service.NotificationService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceImplTest {

    @Mock
    private AnnouncementRepository announcementRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TenantRealtimePublisher tenantRealtimePublisher;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private AttachmentService attachmentService;

    private AnnouncementServiceImpl announcementService;

    @BeforeEach
    void setUp() {
        announcementService = new AnnouncementServiceImpl(
                announcementRepository,
                employeeRepository,
                teamRepository,
                teamMemberRepository,
                authorizationService,
                new TenantDtoMapper(),
                notificationService,
                auditLogService,
                tenantRealtimePublisher,
                emailNotificationService,
                securityUtils,
                attachmentService);

        lenient().doNothing()
                .when(authorizationService)
                .requirePermission(any(Permission.class));
        lenient().when(authorizationService.getCurrentTenantKeyOrThrow())
                .thenReturn("acme");
        lenient().when(notificationService.createSystemNotification(
                        anyLong(), any(), any(), any(), any()))
                .thenReturn(null);
        lenient().when(attachmentService.listAttachments(any(), anyLong()))
                .thenReturn(List.of());
        lenient().when(securityUtils.getCurrentUserEmailOrThrow())
                .thenReturn("current@worknest.test");
        PlatformUserPrincipal principal = org.mockito.Mockito.mock(
                PlatformUserPrincipal.class);
        lenient().when(principal.getId()).thenReturn(100L);
        lenient().when(securityUtils.getCurrentPrincipalOrThrow())
                .thenReturn(principal);
        lenient().when(authorizationService.getCurrentRoleOrThrow())
                .thenReturn(PlatformRole.HR);
    }

    @Test
    void createUsesOnlySimplifiedAnnouncementFields() {
        Employee creator = employee(1L, PlatformRole.HR);
        when(authorizationService.getCurrentEmployeeOrThrow())
                .thenReturn(creator);
        when(authorizationService.getCurrentEmployeeOrNull())
                .thenReturn(creator);
        when(employeeRepository.findByStatus(UserStatus.ACTIVE))
                .thenReturn(List.of(creator));
        when(announcementRepository.save(any(Announcement.class)))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 99L));

        AnnouncementCreateRequestDto request = new AnnouncementCreateRequestDto();
        request.setTitle("  Workspace update  ");
        request.setContent("  Office opens at nine.  ");
        request.setPinned(true);

        AnnouncementResponseDto response =
                announcementService.createAnnouncement(request);

        Assertions.assertThat(response.getId()).isEqualTo(99L);
        Assertions.assertThat(response.getTitle()).isEqualTo("Workspace update");
        Assertions.assertThat(response.getContent()).isEqualTo("Office opens at nine.");
        Assertions.assertThat(response.isPinned()).isTrue();
        Assertions.assertThat(response.getTeamId()).isNull();

        ArgumentCaptor<Announcement> captor =
                ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());
        Announcement saved = captor.getValue();
        Assertions.assertThat(saved.getLegacyMessage())
                .isEqualTo("Office opens at nine.");
        Assertions.assertThat(saved.getCreatedBy()).isSameAs(creator);
    }

    @Test
    void listUsesOnlyTeamVisibilityAndPinOrdering() {
        Employee viewer = employee(10L, PlatformRole.EMPLOYEE);
        Announcement regular = announcement(1L, "Regular", viewer);
        regular.setCreatedAt(LocalDateTime.of(2026, 4, 20, 9, 0));
        Announcement pinned = announcement(2L, "Pinned", viewer);
        pinned.setPinned(true);
        pinned.setCreatedAt(LocalDateTime.of(2026, 4, 18, 9, 0));

        when(authorizationService.getCurrentRoleOrThrow())
                .thenReturn(PlatformRole.EMPLOYEE);
        when(authorizationService.getCurrentEmployeeOrNull())
                .thenReturn(viewer);
        when(announcementRepository.findVisibleAnnouncements(10L, false))
                .thenReturn(List.of(regular, pinned));

        List<AnnouncementResponseDto> results =
                announcementService.listAnnouncements();

        Assertions.assertThat(results)
                .extracting(AnnouncementResponseDto::getId)
                .containsExactly(2L, 1L);
        verify(announcementRepository).findVisibleAnnouncements(10L, false);
    }

    @Test
    void updateChangesTitleContentPinAndTeam() {
        Employee hr = employee(20L, PlatformRole.HR);
        Team team = team(7L, "Engineering", hr);
        Announcement announcement = announcement(3L, "Old", hr);
        AnnouncementUpdateRequestDto request = new AnnouncementUpdateRequestDto();
        request.setTitle("Updated");
        request.setContent("Updated content");
        request.setPinned(true);
        request.setTeamId(7L);

        when(authorizationService.getCurrentEmployeeOrNull()).thenReturn(hr);
        when(announcementRepository.findById(3L))
                .thenReturn(Optional.of(announcement));
        when(teamRepository.findById(7L)).thenReturn(Optional.of(team));
        when(announcementRepository.save(announcement))
                .thenReturn(announcement);

        AnnouncementResponseDto response =
                announcementService.updateAnnouncement(3L, request);

        Assertions.assertThat(response.getTitle()).isEqualTo("Updated");
        Assertions.assertThat(response.getContent()).isEqualTo("Updated content");
        Assertions.assertThat(response.isPinned()).isTrue();
        Assertions.assertThat(response.getTeamId()).isEqualTo(7L);
    }

    @Test
    void pinUpdatesOnlyPinnedState() {
        Employee hr = employee(30L, PlatformRole.HR);
        Announcement announcement = announcement(4L, "Notice", hr);
        when(authorizationService.getCurrentEmployeeOrNull()).thenReturn(hr);
        when(announcementRepository.findById(4L))
                .thenReturn(Optional.of(announcement));
        when(announcementRepository.save(announcement))
                .thenReturn(announcement);

        AnnouncementResponseDto response =
                announcementService.setPinned(4L, true);

        Assertions.assertThat(response.isPinned()).isTrue();
        Assertions.assertThat(announcement.getTitle()).isEqualTo("Notice");
        verify(announcementRepository).save(announcement);
    }

    @Test
    void deleteRemovesAttachmentsAndAnnouncementInsteadOfSoftDeleting() {
        Employee hr = employee(40L, PlatformRole.HR);
        Announcement announcement = announcement(5L, "Delete me", hr);
        when(authorizationService.getCurrentEmployeeOrNull()).thenReturn(hr);
        when(announcementRepository.findById(5L))
                .thenReturn(Optional.of(announcement));

        announcementService.deleteAnnouncement(5L);

        verify(attachmentService)
                .listAttachments(AttachmentEntityType.ANNOUNCEMENT, 5L);
        verify(announcementRepository).delete(announcement);
        verify(announcementRepository, never()).save(announcement);
    }

    @Test
    void employeeCannotCreateUpdateDeleteOrPin() {
        doThrow(new ForbiddenOperationException("Forbidden"))
                .when(authorizationService)
                .requirePermission(Permission.SEND_ANNOUNCEMENT);

        Assertions.assertThatThrownBy(() ->
                        announcementService.createAnnouncement(
                                new AnnouncementCreateRequestDto()))
                .isInstanceOf(ForbiddenOperationException.class);
        Assertions.assertThatThrownBy(() ->
                        announcementService.updateAnnouncement(
                                10L,
                                new AnnouncementUpdateRequestDto()))
                .isInstanceOf(ForbiddenOperationException.class);
        Assertions.assertThatThrownBy(() ->
                        announcementService.deleteAnnouncement(10L))
                .isInstanceOf(ForbiddenOperationException.class);
        Assertions.assertThatThrownBy(() ->
                        announcementService.setPinned(10L, true))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void employeeCannotReadAnotherTeamsAnnouncement() {
        Employee outsider = employee(50L, PlatformRole.EMPLOYEE);
        Employee manager = employee(51L, PlatformRole.MANAGER);
        Employee author = employee(52L, PlatformRole.HR);
        Announcement announcement = announcement(6L, "Team only", author);
        announcement.setTeam(team(8L, "Engineering", manager));

        when(authorizationService.getCurrentRoleOrThrow())
                .thenReturn(PlatformRole.EMPLOYEE);
        when(authorizationService.getCurrentEmployeeOrNull())
                .thenReturn(outsider);
        when(announcementRepository.findById(6L))
                .thenReturn(Optional.of(announcement));
        when(teamMemberRepository
                .findFirstByTeamIdAndEmployeeIdAndLeftAtIsNull(8L, 50L))
                .thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() ->
                        announcementService.getAnnouncement(6L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private Announcement persist(Announcement announcement, Long id) {
        announcement.setId(id);
        announcement.setCreatedAt(LocalDateTime.of(2026, 4, 18, 10, 0));
        announcement.setUpdatedAt(LocalDateTime.of(2026, 4, 18, 10, 0));
        return announcement;
    }

    private Employee employee(Long id, PlatformRole role) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmail("user" + id + "@worknest.test");
        employee.setFirstName("Test");
        employee.setLastName("User");
        employee.setEmployeeCode("EMP-" + id);
        employee.setRole(role);
        employee.setStatus(UserStatus.ACTIVE);
        return employee;
    }

    private Announcement announcement(Long id, String title, Employee creator) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setTitle(title);
        announcement.setContent(title + " content");
        announcement.setCreatedBy(creator);
        announcement.setCreatedByName("Test User");
        announcement.setCreatedAt(LocalDateTime.of(2026, 4, 18, 9, 0));
        announcement.setUpdatedAt(LocalDateTime.of(2026, 4, 18, 9, 0));
        return announcement;
    }

    private Team team(Long id, String name, Employee manager) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        team.setManager(manager);
        return team;
    }
}
