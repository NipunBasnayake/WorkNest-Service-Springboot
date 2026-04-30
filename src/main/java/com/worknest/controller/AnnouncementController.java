package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.announcement.AnnouncementCreateRequestDto;
import com.worknest.tenant.dto.announcement.AnnouncementResponseDto;
import com.worknest.tenant.dto.announcement.AnnouncementUpdateRequestDto;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.service.AnnouncementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<AnnouncementResponseDto>> createAnnouncement(
            @Valid @RequestBody AnnouncementCreateRequestDto requestDto) {
        AnnouncementResponseDto response = announcementService.createAnnouncement(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Announcement created successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<AnnouncementResponseDto>> updateAnnouncement(
            @PathVariable("id") Long id,
            @Valid @RequestBody AnnouncementUpdateRequestDto requestDto) {
        AnnouncementResponseDto response = announcementService.updateAnnouncement(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Announcement updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<Void>> deleteAnnouncement(@PathVariable("id") Long id) {
        announcementService.deleteAnnouncement(id);
        return ResponseEntity.ok(ApiResponse.success("Announcement deleted successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<AnnouncementResponseDto>>> listAnnouncements() {
        List<AnnouncementResponseDto> response = announcementService.listAnnouncements();
        return ResponseEntity.ok(ApiResponse.success("Announcements retrieved successfully", response));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<AnnouncementResponseDto>>> listAnnouncementsPaged(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        PagedResultDto<AnnouncementResponseDto> response = announcementService.listAnnouncementsPaged(
                search,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Announcements retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<AnnouncementResponseDto>> getAnnouncement(@PathVariable("id") Long id) {
        AnnouncementResponseDto response = announcementService.getAnnouncement(id);
        return ResponseEntity.ok(ApiResponse.success("Announcement retrieved successfully", response));
    }
}
