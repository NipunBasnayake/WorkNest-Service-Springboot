package com.worknest.tenant.dto.announcement;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.AnnouncementCreatorRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AnnouncementResponseDto {
    private Long id;
    private String title;
    private String content;
    private String message;
    private Long createdByEmployeeId;
    private String createdByName;
    private EmployeeSimpleDto createdBy;
    private AnnouncementCreatorRole createdByRole;
    private boolean pinned;
    private Long teamId;
    private String teamName;
    private boolean ownedByCurrentUser;
    private boolean canEdit;
    private boolean canDelete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
