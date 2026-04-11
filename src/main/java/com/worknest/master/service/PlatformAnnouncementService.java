package com.worknest.master.service;

import com.worknest.master.dto.PlatformAnnouncementCreateRequestDto;
import com.worknest.master.dto.PlatformAnnouncementResponseDto;

import java.util.List;

public interface PlatformAnnouncementService {

    PlatformAnnouncementResponseDto createAnnouncement(PlatformAnnouncementCreateRequestDto requestDto);

    List<PlatformAnnouncementResponseDto> listAnnouncements();

    PlatformAnnouncementResponseDto getAnnouncement(Long announcementId);

    void deleteAnnouncement(Long announcementId);
}
