package com.worknest.controller;

import com.worknest.common.api.ApiResponse;
import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.leave.*;
import com.worknest.tenant.enums.LeaveStatus;
import com.worknest.tenant.service.LeaveRequestService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/leaves")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<LeaveResponseDto>> applyLeave(
            @Valid @RequestBody LeaveApplyRequestDto requestDto) {
        LeaveResponseDto response = leaveRequestService.applyLeave(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Leave request submitted successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<LeaveResponseDto>> updateLeave(
            @PathVariable Long id,
            @Valid @RequestBody LeaveUpdateRequestDto requestDto) {
        LeaveResponseDto response = leaveRequestService.updateLeave(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Leave request updated successfully", response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<LeaveResponseDto>>> listMyLeaveRequests() {
        List<LeaveResponseDto> response = leaveRequestService.listMyLeaveRequests();
        return ResponseEntity.ok(ApiResponse.success("My leave requests retrieved", response));
    }

    @GetMapping("/my/paged")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<PagedResultDto<LeaveResponseDto>>> listMyLeaveRequestsPaged(
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PagedResultDto<LeaveResponseDto> response = leaveRequestService.listMyLeaveRequestsPaged(
                status,
                fromDate,
                toDate,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("My leave requests retrieved", response));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<List<LeaveResponseDto>>> listPendingLeaveRequests() {
        List<LeaveResponseDto> response = leaveRequestService.listPendingLeaveRequests();
        return ResponseEntity.ok(ApiResponse.success("Pending leave requests retrieved", response));
    }

    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<PagedResultDto<LeaveResponseDto>>> listLeaveRequestsPaged(
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        PagedResultDto<LeaveResponseDto> response = leaveRequestService.listLeaveRequestsPaged(
                status,
                fromDate,
                toDate,
                page,
                size,
                sortBy,
                sortDir
        );
        return ResponseEntity.ok(ApiResponse.success("Leave requests retrieved", response));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<LeaveResponseDto>> approveLeave(
            @PathVariable Long id,
            @Valid @RequestBody LeaveDecisionRequestDto requestDto) {
        LeaveResponseDto response = leaveRequestService.approveLeave(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Leave request approved", response));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','ADMIN','HR')")
    public ResponseEntity<ApiResponse<LeaveResponseDto>> rejectLeave(
            @PathVariable Long id,
            @Valid @RequestBody LeaveDecisionRequestDto requestDto) {
        LeaveResponseDto response = leaveRequestService.rejectLeave(id, requestDto);
        return ResponseEntity.ok(ApiResponse.success("Leave request rejected", response));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<LeaveResponseDto>> cancelLeave(@PathVariable Long id) {
        LeaveResponseDto response = leaveRequestService.cancelLeave(id);
        return ResponseEntity.ok(ApiResponse.success("Leave request cancelled", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR','EMPLOYEE')")
    public ResponseEntity<ApiResponse<LeaveResponseDto>> getLeaveDetails(@PathVariable Long id) {
        LeaveResponseDto response = leaveRequestService.getLeaveDetails(id);
        return ResponseEntity.ok(ApiResponse.success("Leave request retrieved", response));
    }
}
