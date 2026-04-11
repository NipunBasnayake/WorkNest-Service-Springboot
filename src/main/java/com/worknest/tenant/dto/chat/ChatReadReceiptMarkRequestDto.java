package com.worknest.tenant.dto.chat;

import com.worknest.tenant.enums.ChatType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatReadReceiptMarkRequestDto {

    @NotNull(message = "Chat type is required")
    private ChatType chatType;

    @NotNull(message = "Message ID is required")
    @Positive(message = "Message ID must be positive")
    private Long messageId;
}
