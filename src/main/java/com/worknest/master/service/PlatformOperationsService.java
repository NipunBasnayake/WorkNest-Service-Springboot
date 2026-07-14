package com.worknest.master.service;

import com.worknest.master.dto.PlatformAuditEventResponseDto;
import com.worknest.master.dto.PlatformOperationsSnapshotDto;
import com.worknest.master.dto.PlatformUserResponseDto;

import java.util.List;

public interface PlatformOperationsService {
    PlatformOperationsSnapshotDto getSnapshot();

    List<PlatformUserResponseDto> getUsers();

    List<PlatformAuditEventResponseDto> getAuditEvents();
}
