package com.worknest.tenant.service;

import com.worknest.tenant.dto.settings.WorkspaceProfileResponseDto;
import com.worknest.tenant.dto.settings.WorkspaceProfileUpdateRequestDto;

public interface WorkspaceSettingsService {

    WorkspaceProfileResponseDto getWorkspaceProfile();

    WorkspaceProfileResponseDto updateWorkspaceProfile(WorkspaceProfileUpdateRequestDto requestDto);
}
