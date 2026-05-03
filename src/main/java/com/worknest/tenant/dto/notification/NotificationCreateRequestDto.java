package com.worknest.tenant.dto.notification;

import com.worknest.tenant.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationCreateRequestDto {

    @NotNull(message = "Recipient employee ID is required")
    @Positive(message = "Recipient employee ID must be positive")
    private Long recipientEmployeeId;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotBlank(message = "Notification message is required")
    @Size(max = 500, message = "Notification message must not exceed 500 characters")
    private String message;

    @Size(max = 80, message = "Reference type must not exceed 80 characters")
    private String referenceType;

    @Size(max = 80, message = "Related entity type must not exceed 80 characters")
    private String relatedEntityType;

    @Positive(message = "Reference ID must be positive")
    private Long referenceId;

    @Positive(message = "Related entity ID must be positive")
    private Long relatedEntityId;

    @Positive(message = "Announcement ID must be positive")
    private Long announcementId;
}
