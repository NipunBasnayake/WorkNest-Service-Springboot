package com.worknest.tenant.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class DashboardAnnouncementItemDto {
    private Long id;
    private String title;
    private String message;
    private String createdByName;
    private LocalDateTime createdAt;
}
