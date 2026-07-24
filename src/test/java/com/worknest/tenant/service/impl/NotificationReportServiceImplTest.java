package com.worknest.tenant.service.impl;

import com.worknest.security.authorization.AuthorizationService;
import com.worknest.tenant.dto.report.NotificationReportPageDto;
import com.worknest.tenant.entity.Employee;
import com.worknest.tenant.entity.Notification;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationReportServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AuthorizationService authorizationService;

    private NotificationReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new NotificationReportServiceImpl(notificationRepository, authorizationService);
    }

    @Test
    void derivesReadTotalsPercentagesAndChartsFromTheSameFilteredNotifications() {
        List<Notification> notifications = List.of(
                notification(1L, NotificationType.TASK_ASSIGNED, "TASK", false, LocalDateTime.of(2026, 7, 1, 9, 0)),
                notification(2L, NotificationType.ANNOUNCEMENT, "ANNOUNCEMENT", true, LocalDateTime.of(2026, 7, 2, 10, 0)));
        when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(notifications, PageRequest.of(0, 20), notifications.size()));
        when(notificationRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(notifications);

        NotificationReportPageDto report = reportService.getReport(
                null,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                0,
                20,
                null,
                "desc");

        assertThat(report.getTotalElements()).isEqualTo(2);
        assertThat(report.getSummary()).containsEntry("Notifications", 2L)
                .containsEntry("Read", 1L)
                .containsEntry("Unread", 1L)
                .containsEntry("Read %", "50.0%")
                .containsEntry("Unread %", "50.0%");
        Map<String, Object> readChart = chart(report, "Notifications by Read Status");
        assertThat(chartTotal(readChart)).isEqualTo(2);
        assertThat(chart(report, "Notifications by Type").get("data")).asList().hasSize(2);
        assertThat(report.getSupportingCharts())
                .extracting(chart -> chart.get("title"))
                .doesNotContain("Notifications by Priority");
    }

    private Map<String, Object> chart(NotificationReportPageDto report, String title) {
        return report.getSupportingCharts().stream()
                .filter(chart -> title.equals(chart.get("title")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private long chartTotal(Map<String, Object> chart) {
        return ((List<Map<String, Object>>) chart.get("data")).stream()
                .mapToLong(point -> ((Number) point.get("value")).longValue())
                .sum();
    }

    private Notification notification(
            Long id,
            NotificationType type,
            String category,
            boolean read,
            LocalDateTime createdAt) {
        Employee recipient = new Employee();
        recipient.setId(10L + id);
        recipient.setFirstName("Employee");
        recipient.setLastName(id.toString());
        recipient.setDepartment("Engineering");

        Notification notification = new Notification();
        notification.setId(id);
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setMessage("Message " + id);
        notification.setRelatedEntityType(category);
        notification.setRelatedEntityId(100L + id);
        notification.setRead(read);
        notification.setCreatedAt(createdAt);
        return notification;
    }
}
