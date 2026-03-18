package com.worknest.tenant.dto.dashboard;

import com.worknest.tenant.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class DashboardNotificationItemDto {
    private Long id;
    private NotificationType type;
    private String message;
    private boolean read;
    private String referenceType;
    private Long referenceId;
    private LocalDateTime createdAt;
}
