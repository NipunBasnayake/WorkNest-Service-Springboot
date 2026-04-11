package com.worknest.tenant.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamChatMessageSendRequestDto {

    @Deprecated
    @Positive(message = "Sender employee ID must be positive")
    private Long senderEmployeeId;

    @NotBlank(message = "Message is required")
    @Size(max = 5000, message = "Message must not exceed 5000 characters")
    private String message;
}
