package com.worknest.tenant.service.impl;

import com.worknest.tenant.dto.announcement.AnnouncementResponseDto;
import com.worknest.tenant.dto.chat.HrMessageResponseDto;
import com.worknest.tenant.dto.chat.TeamChatMessageResponseDto;
import com.worknest.tenant.dto.notification.NotificationResponseDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class TenantRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public TenantRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishAnnouncement(String tenantKey, AnnouncementResponseDto payload) {
        messagingTemplate.convertAndSend("/topic/tenant/" + tenantKey + "/announcements", payload);
    }

    public void publishNotification(String recipientEmail, NotificationResponseDto payload) {
        messagingTemplate.convertAndSendToUser(recipientEmail, "/queue/notifications", payload);
    }

    public void publishHrMessage(String tenantKey, Long conversationId, HrMessageResponseDto payload) {
        messagingTemplate.convertAndSend(
                "/topic/tenant/" + tenantKey + "/hr-chat/" + conversationId,
                payload
        );
    }

    public void publishTeamMessage(String tenantKey, Long teamChatId, TeamChatMessageResponseDto payload) {
        messagingTemplate.convertAndSend(
                "/topic/tenant/" + tenantKey + "/team-chat/" + teamChatId,
                payload
        );
    }
}
