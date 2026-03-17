package com.worknest.tenant.dto.team;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TeamResponseDto {
    private Long id;
    private String name;
    private EmployeeSimpleDto manager;
    private long activeMemberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
