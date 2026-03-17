package com.worknest.tenant.service;

import com.worknest.tenant.dto.announcement.AnnouncementCreateRequestDto;
import com.worknest.tenant.dto.announcement.AnnouncementResponseDto;
import com.worknest.tenant.dto.announcement.AnnouncementUpdateRequestDto;
import com.worknest.tenant.dto.common.PagedResultDto;

import java.util.List;

public interface AnnouncementService {

    AnnouncementResponseDto createAnnouncement(AnnouncementCreateRequestDto requestDto);

    AnnouncementResponseDto updateAnnouncement(Long announcementId, AnnouncementUpdateRequestDto requestDto);

    void deleteAnnouncement(Long announcementId);

    List<AnnouncementResponseDto> listAnnouncements();

    PagedResultDto<AnnouncementResponseDto> listAnnouncementsPaged(String search, int page, int size, String sortBy, String sortDir);

    AnnouncementResponseDto getAnnouncement(Long announcementId);
}
