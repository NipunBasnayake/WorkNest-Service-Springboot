package com.worknest.tenant.dto.announcement;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
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
    private String message;
    private EmployeeSimpleDto createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
