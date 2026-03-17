package com.worknest.tenant.dto.chat;

import com.worknest.tenant.enums.ChatType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatReadReceiptMarkRequestDto {

    @NotNull(message = "Chat type is required")
    private ChatType chatType;

    @NotNull(message = "Message ID is required")
    private Long messageId;
}
