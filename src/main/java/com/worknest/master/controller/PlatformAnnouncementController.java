package com.worknest.master.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.PlatformAnnouncementCreateRequestDto;
import com.worknest.master.dto.PlatformAnnouncementResponseDto;
import com.worknest.master.service.PlatformAnnouncementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform/announcements")
public class PlatformAnnouncementController {

    private final PlatformAnnouncementService platformAnnouncementService;

    public PlatformAnnouncementController(PlatformAnnouncementService platformAnnouncementService) {
        this.platformAnnouncementService = platformAnnouncementService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<PlatformAnnouncementResponseDto>> createAnnouncement(
            @Valid @RequestBody PlatformAnnouncementCreateRequestDto requestDto) {
        PlatformAnnouncementResponseDto response = platformAnnouncementService.createAnnouncement(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Platform announcement sent successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<PlatformAnnouncementResponseDto>>> listAnnouncements() {
        List<PlatformAnnouncementResponseDto> response = platformAnnouncementService.listAnnouncements();
        return ResponseEntity.ok(ApiResponse.success("Platform announcements retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<PlatformAnnouncementResponseDto>> getAnnouncement(@PathVariable("id") Long id) {
        PlatformAnnouncementResponseDto response = platformAnnouncementService.getAnnouncement(id);
        return ResponseEntity.ok(ApiResponse.success("Platform announcement retrieved successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAnnouncement(@PathVariable("id") Long id) {
        platformAnnouncementService.deleteAnnouncement(id);
        return ResponseEntity.ok(ApiResponse.success("Platform announcement deleted successfully"));
    }
}
