package com.worknest.master.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.master.dto.PlatformAuditEventResponseDto;
import com.worknest.master.dto.PlatformOperationsSnapshotDto;
import com.worknest.master.dto.PlatformUserResponseDto;
import com.worknest.master.service.PlatformOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/operations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformOperationsController {

    private final PlatformOperationsService platformOperationsService;

    @GetMapping("/snapshot")
    public ResponseEntity<ApiResponse<PlatformOperationsSnapshotDto>> getSnapshot() {
        return ResponseEntity.ok(ApiResponse.success(
                "Platform operations snapshot retrieved successfully",
                platformOperationsService.getSnapshot()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<PlatformUserResponseDto>>> getUsers() {
        return ResponseEntity.ok(ApiResponse.success(
                "Platform users retrieved successfully",
                platformOperationsService.getUsers()));
    }

    @GetMapping("/audit-events")
    public ResponseEntity<ApiResponse<List<PlatformAuditEventResponseDto>>> getAuditEvents() {
        return ResponseEntity.ok(ApiResponse.success(
                "Platform audit events retrieved successfully",
                platformOperationsService.getAuditEvents()));
    }
}
