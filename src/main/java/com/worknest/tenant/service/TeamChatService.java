package com.worknest.tenant.service;

import com.worknest.tenant.dto.chat.*;

import java.util.List;

public interface TeamChatService {

    TeamChatResponseDto createOrGetTeamChat(TeamChatCreateRequestDto requestDto);

    TeamChatResponseDto getOrCreateByTeam(Long teamId);

    List<TeamChatResponseDto> listMyTeamChats();

    List<TeamChatMessageResponseDto> listMessages(Long teamChatId);

    TeamChatMessageResponseDto sendMessage(Long teamChatId, TeamChatMessageSendRequestDto requestDto);
}
