package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.announcement.AnnouncementCreateRequestDto;
import com.worknest.tenant.dto.announcement.AnnouncementResponseDto;
import com.worknest.tenant.entity.Announcement;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.enums.AuditEntityType;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.AnnouncementRepository;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.TeamMemberRepository;
import com.worknest.tenant.repository.TeamRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
    private com.worknest.notification.email.EmailNotificationService emailNotificationService;

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
                emailNotificationService
        );

        doNothing().when(authorizationService).requirePermission(any(Permission.class));
        when(authorizationService.getCurrentTenantKeyOrThrow()).thenReturn("acme");
        when(notificationService.createSystemNotification(anyLong(), any(), any(), any(), any())).thenReturn(null);
    }

    @Test
    void createAnnouncementSavesEntityAndCreatesAnnouncementLinkedNotifications() {
        Employee creator = employee(1L, "creator@worknest.test", PlatformRole.HR);
        Employee recipientA = employee(2L, "emp.a@worknest.test", PlatformRole.EMPLOYEE);
        Employee recipientB = employee(3L, "emp.b@worknest.test", PlatformRole.EMPLOYEE);

        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(creator);
        when(authorizationService.getCurrentRoleOrThrow()).thenReturn(PlatformRole.HR);
        when(employeeRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(recipientA, recipientB));
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(invocation -> {
            Announcement saved = invocation.getArgument(0);
            saved.setId(99L);
            saved.setCreatedAt(LocalDateTime.of(2026, 4, 18, 10, 0));
            saved.setUpdatedAt(LocalDateTime.of(2026, 4, 18, 10, 0));
            return saved;
        });

        AnnouncementCreateRequestDto request = new AnnouncementCreateRequestDto();
        request.setTitle("  Workspace policy update  ");
        request.setMessage("  Please submit timesheets by Friday.  ");

        AnnouncementResponseDto response = announcementService.createAnnouncement(request);

        Assertions.assertThat(response.getId()).isEqualTo(99L);
        Assertions.assertThat(response.getTitle()).isEqualTo("Workspace policy update");
        Assertions.assertThat(response.getMessage()).isEqualTo("Please submit timesheets by Friday.");

        ArgumentCaptor<Announcement> announcementCaptor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(announcementCaptor.capture());
        Announcement persisted = announcementCaptor.getValue();
        Assertions.assertThat(persisted.getTitle()).isEqualTo("Workspace policy update");
        Assertions.assertThat(persisted.getMessage()).isEqualTo("Please submit timesheets by Friday.");
        Assertions.assertThat(persisted.getCreatedBy().getId()).isEqualTo(creator.getId());

        verify(notificationService).createSystemNotification(
                eq(2L),
                eq(NotificationType.ANNOUNCEMENT),
                eq("New announcement: Workspace policy update"),
                eq(AuditEntityType.ANNOUNCEMENT.name()),
                eq(99L)
        );
        verify(notificationService).createSystemNotification(
                eq(3L),
                eq(NotificationType.ANNOUNCEMENT),
                eq("New announcement: Workspace policy update"),
                eq(AuditEntityType.ANNOUNCEMENT.name()),
                eq(99L)
        );
    }

    @Test
    void listAnnouncementsUsesVisibilityContextForRegularEmployee() {
        Employee me = employee(10L, "me@worknest.test", PlatformRole.EMPLOYEE);
        Announcement announcement = announcement(44L, "Team update", "Sprint review at 4 PM", me);

        when(authorizationService.getCurrentRoleOrThrow()).thenReturn(PlatformRole.EMPLOYEE);
        when(authorizationService.getCurrentEmployeeOrNull()).thenReturn(me);
        when(announcementRepository.findVisibleAnnouncements(10L, false)).thenReturn(List.of(announcement));

        List<AnnouncementResponseDto> results = announcementService.listAnnouncements();

        Assertions.assertThat(results).hasSize(1);
        Assertions.assertThat(results.getFirst().getId()).isEqualTo(44L);
        verify(announcementRepository).findVisibleAnnouncements(10L, false);
    }

    @Test
    void getAnnouncementReturnsDetailWhenAccessible() {
        Employee me = employee(12L, "reader@worknest.test", PlatformRole.EMPLOYEE);
        Announcement announcement = announcement(55L, "Office closed", "Holiday on Monday", me);

        when(authorizationService.getCurrentRoleOrThrow()).thenReturn(PlatformRole.EMPLOYEE);
        when(authorizationService.getCurrentEmployeeOrNull()).thenReturn(me);
        when(announcementRepository.findById(55L)).thenReturn(Optional.of(announcement));

        AnnouncementResponseDto result = announcementService.getAnnouncement(55L);

        Assertions.assertThat(result.getId()).isEqualTo(55L);
        Assertions.assertThat(result.getTitle()).isEqualTo("Office closed");
        Assertions.assertThat(result.getMessage()).isEqualTo("Holiday on Monday");
    }

    private Employee employee(Long id, String email, PlatformRole role) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmail(email);
        employee.setFirstName("Test");
        employee.setLastName("User");
        employee.setEmployeeCode("EMP-" + id);
        employee.setRole(role);
        employee.setStatus(UserStatus.ACTIVE);
        return employee;
    }

    private Announcement announcement(Long id, String title, String message, Employee creator) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setTitle(title);
        announcement.setMessage(message);
        announcement.setCreatedBy(creator);
        announcement.setCreatedAt(LocalDateTime.of(2026, 4, 18, 9, 0));
        announcement.setUpdatedAt(LocalDateTime.of(2026, 4, 18, 9, 0));
        return announcement;
    }
}
