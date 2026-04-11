package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.chat.HrConversationCreateRequestDto;
import com.worknest.tenant.dto.chat.HrConversationResponseDto;
import com.worknest.tenant.dto.chat.HrConversationTargetsResponseDto;
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
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<HrConversationResponseDto>> createOrGetConversation(
            @Valid @RequestBody HrConversationCreateRequestDto requestDto) {
        HrConversationResponseDto response = hrChatService.createOrGetConversation(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("HR conversation ready", response));
    }

    @GetMapping("/targets")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<HrConversationTargetsResponseDto>> listConversationTargets() {
        HrConversationTargetsResponseDto response = hrChatService.listConversationTargets();
        return ResponseEntity.ok(ApiResponse.success("HR conversation targets retrieved successfully", response));
    }

    @GetMapping("/conversations/my")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<HrConversationResponseDto>>> listMyConversations() {
        List<HrConversationResponseDto> response = hrChatService.listMyConversations();
        return ResponseEntity.ok(ApiResponse.success("HR conversations retrieved successfully", response));
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<HrMessageResponseDto>>> listMessages(@PathVariable("id") Long id) {
        List<HrMessageResponseDto> response = hrChatService.listMessages(id);
        return ResponseEntity.ok(ApiResponse.success("HR messages retrieved successfully", response));
    }

    @PostMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<HrMessageResponseDto>> sendMessage(
            @PathVariable("id") Long id,
            @Valid @RequestBody HrMessageSendRequestDto requestDto) {
        HrMessageResponseDto response = hrChatService.sendMessage(id, requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("HR message sent successfully", response));
    }

    @PatchMapping("/conversations/{id}/read")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Long>> markAsRead(@PathVariable("id") Long id) {
        long count = hrChatService.markConversationMessagesAsRead(id);
        return ResponseEntity.ok(ApiResponse.success("HR messages marked as read", count));
    }
}
