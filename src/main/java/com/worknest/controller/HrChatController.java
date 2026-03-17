package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.chat.HrConversationCreateRequestDto;
import com.worknest.tenant.dto.chat.HrConversationResponseDto;
import com.worknest.tenant.dto.chat.HrMessageResponseDto;
import com.worknest.tenant.dto.chat.HrMessageSendRequestDto;
import com.worknest.tenant.service.HrChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/chats/hr")
public class HrChatController {

    private final HrChatService hrChatService;

    public HrChatController(HrChatService hrChatService) {
        this.hrChatService = hrChatService;
    }

    @PostMapping("/conversations")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<HrConversationResponseDto>> createOrGetConversation(
            @Valid @RequestBody HrConversationCreateRequestDto requestDto) {
        HrConversationResponseDto response = hrChatService.createOrGetConversation(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("HR conversation ready", response));
    }

    @GetMapping("/conversations/my")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<HrConversationResponseDto>>> listMyConversations() {
        List<HrConversationResponseDto> response = hrChatService.listMyConversations();
        return ResponseEntity.ok(ApiResponse.success("HR conversations retrieved successfully", response));
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<HrMessageResponseDto>>> listMessages(@PathVariable Long id) {
        List<HrMessageResponseDto> response = hrChatService.listMessages(id);
        return ResponseEntity.ok(ApiResponse.success("HR messages retrieved successfully", response));
    }

    @PostMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<HrMessageResponseDto>> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody HrMessageSendRequestDto requestDto) {
        HrMessageResponseDto response = hrChatService.sendMessage(id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("HR message sent successfully", response));
    }

    @PatchMapping("/conversations/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Long>> markAsRead(@PathVariable Long id) {
        long count = hrChatService.markConversationMessagesAsRead(id);
        return ResponseEntity.ok(ApiResponse.success("HR messages marked as read", count));
    }
}
