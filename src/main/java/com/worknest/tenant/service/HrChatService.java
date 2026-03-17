package com.worknest.tenant.service;

import com.worknest.tenant.dto.chat.*;

import java.util.List;

public interface HrChatService {

    HrConversationResponseDto createOrGetConversation(HrConversationCreateRequestDto requestDto);

    HrConversationTargetsResponseDto listConversationTargets();

    List<HrConversationResponseDto> listMyConversations();

    List<HrMessageResponseDto> listMessages(Long conversationId);

    HrMessageResponseDto sendMessage(Long conversationId, HrMessageSendRequestDto requestDto);

    long markConversationMessagesAsRead(Long conversationId);
}
