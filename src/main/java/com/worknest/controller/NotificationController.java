package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.notification.NotificationCreateRequestDto;
import com.worknest.tenant.dto.notification.NotificationResponseDto;
import com.worknest.tenant.dto.notification.NotificationUnreadCountDto;
import com.worknest.tenant.enums.NotificationType;
import com.worknest.tenant.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR')")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> createNotification(
            @Valid @RequestBody NotificationCreateRequestDto requestDto) {
        NotificationResponseDto response = notificationService.createNotification(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Notification created successfully", response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> listMyNotifications() {
        List<NotificationResponseDto> response = notificationService.listMyNotifications();
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully", response));
    }

    @GetMapping("/my/paged")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<NotificationResponseDto>>> listMyNotificationsPaged(
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PagedResultDto<NotificationResponseDto> response = notificationService.listMyNotificationsPaged(
                read,
                type,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully", response));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> markAsRead(@PathVariable Long id) {
        NotificationResponseDto response = notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", response));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Long>> markAllAsRead() {
        long count = notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.success("Notifications marked as read", count));
    }

    @GetMapping("/my/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<NotificationUnreadCountDto>> getUnreadCount() {
        NotificationUnreadCountDto response = NotificationUnreadCountDto.builder()
                .unreadCount(notificationService.getMyUnreadCount())
                .build();
        return ResponseEntity.ok(ApiResponse.success("Unread notification count retrieved", response));
    }
}
