package com.worknest.tenant.dto.notification;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class NotificationUnreadCountDto {
    private long unreadCount;
}
