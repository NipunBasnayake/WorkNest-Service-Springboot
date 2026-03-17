package com.worknest.tenant.service;

import com.worknest.tenant.dto.notification.NotificationCreateRequestDto;
import com.worknest.tenant.dto.notification.NotificationResponseDto;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.enums.NotificationType;

import java.util.List;

public interface NotificationService {

    NotificationResponseDto createNotification(NotificationCreateRequestDto requestDto);

    NotificationResponseDto createSystemNotification(
            Long recipientEmployeeId,
            NotificationType type,
            String message,
            String referenceType,
            Long referenceId);

    List<NotificationResponseDto> listMyNotifications();

    PagedResultDto<NotificationResponseDto> listMyNotificationsPaged(
            Boolean readFilter,
            NotificationType type,
            int page,
            int size,
            String sortBy,
            String sortDir);

    NotificationResponseDto markAsRead(Long notificationId);

    long markAllAsRead();

    long getMyUnreadCount();
}
