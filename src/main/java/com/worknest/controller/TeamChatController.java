package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.chat.TeamChatCreateRequestDto;
import com.worknest.tenant.dto.chat.TeamChatMessageResponseDto;
import com.worknest.tenant.dto.chat.TeamChatMessageSendRequestDto;
import com.worknest.tenant.dto.chat.TeamChatResponseDto;
import com.worknest.tenant.service.TeamChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/chats/team")
public class TeamChatController {

    private final TeamChatService teamChatService;

    public TeamChatController(TeamChatService teamChatService) {
        this.teamChatService = teamChatService;
    }

    @PostMapping("/conversations")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TeamChatResponseDto>> createOrGetTeamChat(
            @Valid @RequestBody TeamChatCreateRequestDto requestDto) {
        TeamChatResponseDto response = teamChatService.createOrGetTeamChat(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team chat ready", response));
    }

    @GetMapping("/conversations/team/{teamId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TeamChatResponseDto>> getOrCreateByTeam(@PathVariable("teamId") Long teamId) {
        TeamChatResponseDto response = teamChatService.getOrCreateByTeam(teamId);
        return ResponseEntity.ok(ApiResponse.success("Team chat retrieved successfully", response));
    }

    @GetMapping("/conversations/my")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TeamChatResponseDto>>> listMyTeamChats() {
        List<TeamChatResponseDto> response = teamChatService.listMyTeamChats();
        return ResponseEntity.ok(ApiResponse.success("Team chats retrieved successfully", response));
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<TeamChatMessageResponseDto>>> listMessages(@PathVariable("id") Long id) {
        List<TeamChatMessageResponseDto> response = teamChatService.listMessages(id);
        return ResponseEntity.ok(ApiResponse.success("Team chat messages retrieved successfully", response));
    }

    @PostMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<TeamChatMessageResponseDto>> sendMessage(
            @PathVariable("id") Long id,
            @Valid @RequestBody TeamChatMessageSendRequestDto requestDto) {
        TeamChatMessageResponseDto response = teamChatService.sendMessage(id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team chat message sent successfully", response));
    }
}
