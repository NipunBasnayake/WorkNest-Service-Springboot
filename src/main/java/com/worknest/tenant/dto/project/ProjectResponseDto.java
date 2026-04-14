package com.worknest.tenant.dto.project;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.dto.attachment.AttachmentResponseDto;
import com.worknest.tenant.enums.ProjectStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class ProjectResponseDto {
    private Long id;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private ProjectStatus status;
    private EmployeeSimpleDto createdBy;
    private Integer teamCount;
    private List<Long> teamIds;
    private List<AttachmentResponseDto> attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
