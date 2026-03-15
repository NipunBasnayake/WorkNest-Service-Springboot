package com.worknest.tenant.service;

import com.worknest.tenant.dto.chat.ChatReadReceiptResponseDto;
import com.worknest.tenant.enums.ChatType;

import java.util.List;

public interface ChatReadReceiptService {

    ChatReadReceiptResponseDto markAsRead(ChatType chatType, Long messageId);

    List<ChatReadReceiptResponseDto> listReceipts(ChatType chatType, Long messageId);
}
