package com.worknest.master.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PlatformAnnouncementResponseDto {
    private Long id;
    private String title;
    private String message;
    private Long createdById;
    private String createdByName;
    private String createdByEmail;
    private LocalDateTime createdAt;
}
