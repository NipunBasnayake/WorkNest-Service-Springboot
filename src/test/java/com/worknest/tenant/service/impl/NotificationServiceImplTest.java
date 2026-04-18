package com.worknest.tenant.service.impl;

import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.security.authorization.AuthorizationService;
import com.worknest.security.authorization.Permission;
import com.worknest.tenant.dto.notification.NotificationCreateRequestDto;
import com.worknest.tenant.dto.notification.NotificationResponseDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Notification;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.EmployeeRepository;
import com.worknest.tenant.repository.NotificationRepository;
import com.worknest.tenant.service.AuditLogService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private TenantRealtimePublisher tenantRealtimePublisher;

    @Mock
    private AuditLogService auditLogService;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                employeeRepository,
                authorizationService,
                new TenantDtoMapper(),
                tenantRealtimePublisher,
                auditLogService
        );

        doNothing().when(authorizationService).requirePermission(any(Permission.class));
    }

    @Test
    void createNotificationAcceptsRelatedEntityFieldsAndMapsAnnouncementLinkage() {
        Employee recipient = employee(7L, "recipient@worknest.test");
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(recipient));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(501L);
            notification.setCreatedAt(LocalDateTime.of(2026, 4, 18, 11, 15));
            return notification;
        });

        NotificationCreateRequestDto request = new NotificationCreateRequestDto();
        request.setRecipientEmployeeId(7L);
        request.setType(NotificationType.ANNOUNCEMENT);
        request.setMessage("New announcement: Policy update");
        request.setRelatedEntityType("ANNOUNCEMENT");
        request.setRelatedEntityId(88L);

        NotificationResponseDto response = notificationService.createNotification(request);

        Assertions.assertThat(response.getId()).isEqualTo(501L);
        Assertions.assertThat(response.getReferenceType()).isEqualTo("ANNOUNCEMENT");
        Assertions.assertThat(response.getReferenceId()).isEqualTo(88L);
        Assertions.assertThat(response.getRelatedEntityType()).isEqualTo("ANNOUNCEMENT");
        Assertions.assertThat(response.getRelatedEntityId()).isEqualTo(88L);
        Assertions.assertThat(response.getAnnouncementId()).isEqualTo(88L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification persisted = captor.getValue();
        Assertions.assertThat(persisted.getReferenceType()).isEqualTo("ANNOUNCEMENT");
        Assertions.assertThat(persisted.getReferenceId()).isEqualTo(88L);
    }

    @Test
    void listMyNotificationsIncludesBackwardCompatibleAnnouncementMetadata() {
        Employee me = employee(9L, "me@worknest.test");
        Notification notification = new Notification();
        notification.setId(300L);
        notification.setRecipient(me);
        notification.setType(NotificationType.ANNOUNCEMENT);
        notification.setMessage("New announcement available");
        notification.setReferenceType("ANNOUNCEMENT");
        notification.setReferenceId(77L);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.of(2026, 4, 18, 8, 0));

        when(authorizationService.getCurrentEmployeeOrThrow()).thenReturn(me);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(notification));

        List<NotificationResponseDto> response = notificationService.listMyNotifications();

        Assertions.assertThat(response).hasSize(1);
        NotificationResponseDto dto = response.getFirst();
        Assertions.assertThat(dto.getReferenceType()).isEqualTo("ANNOUNCEMENT");
        Assertions.assertThat(dto.getReferenceId()).isEqualTo(77L);
        Assertions.assertThat(dto.getRelatedEntityType()).isEqualTo("ANNOUNCEMENT");
        Assertions.assertThat(dto.getRelatedEntityId()).isEqualTo(77L);
        Assertions.assertThat(dto.getAnnouncementId()).isEqualTo(77L);

        verify(notificationRepository).findByRecipientIdOrderByCreatedAtDesc(eq(9L));
    }

    private Employee employee(Long id, String email) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmail(email);
        employee.setFirstName("Test");
        employee.setLastName("User");
        employee.setEmployeeCode("EMP-" + id);
        employee.setRole(PlatformRole.EMPLOYEE);
        employee.setStatus(UserStatus.ACTIVE);
        return employee;
    }
}
