package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.chat.ChatReadReceiptMarkRequestDto;
import com.worknest.tenant.dto.chat.ChatReadReceiptResponseDto;
import com.worknest.tenant.enums.ChatType;
import com.worknest.tenant.service.ChatReadReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/chats/read-receipts")
public class ChatReadReceiptController {

    private final ChatReadReceiptService chatReadReceiptService;

    public ChatReadReceiptController(ChatReadReceiptService chatReadReceiptService) {
        this.chatReadReceiptService = chatReadReceiptService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ChatReadReceiptResponseDto>> markAsRead(
            @Valid @RequestBody ChatReadReceiptMarkRequestDto requestDto) {
        ChatReadReceiptResponseDto response = chatReadReceiptService
                .markAsRead(requestDto.getChatType(), requestDto.getMessageId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Read receipt marked successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<ChatReadReceiptResponseDto>>> listReceipts(
            @RequestParam("chatType") ChatType chatType,
            @RequestParam("messageId") Long messageId) {
        List<ChatReadReceiptResponseDto> response = chatReadReceiptService.listReceipts(chatType, messageId);
        return ResponseEntity.ok(ApiResponse.success("Read receipts retrieved successfully", response));
    }
}
