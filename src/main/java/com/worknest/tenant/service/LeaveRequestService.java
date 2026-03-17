package com.worknest.tenant.service;

import com.worknest.tenant.dto.common.PagedResultDto;
import com.worknest.tenant.dto.leave.*;
import com.worknest.tenant.enums.LeaveStatus;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestService {

    LeaveResponseDto applyLeave(LeaveApplyRequestDto requestDto);

    LeaveResponseDto updateLeave(Long leaveRequestId, LeaveUpdateRequestDto requestDto);

    List<LeaveResponseDto> listMyLeaveRequests();

    PagedResultDto<LeaveResponseDto> listMyLeaveRequestsPaged(
            LeaveStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sortBy,
            String sortDir);

    List<LeaveResponseDto> listPendingLeaveRequests();

    PagedResultDto<LeaveResponseDto> listLeaveRequestsPaged(
            LeaveStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size,
            String sortBy,
            String sortDir);

    LeaveResponseDto approveLeave(Long leaveRequestId, LeaveDecisionRequestDto requestDto);

    LeaveResponseDto rejectLeave(Long leaveRequestId, LeaveDecisionRequestDto requestDto);

    LeaveResponseDto cancelLeave(Long leaveRequestId);

    LeaveResponseDto getLeaveDetails(Long leaveRequestId);
}
