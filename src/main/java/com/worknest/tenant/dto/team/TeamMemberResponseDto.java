package com.worknest.tenant.dto.team;

import com.worknest.tenant.dto.common.EmployeeSimpleDto;
import com.worknest.tenant.enums.TeamFunctionalRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TeamMemberResponseDto {
    private Long id;
    private Long teamId;
    private EmployeeSimpleDto employee;
    private TeamFunctionalRole functionalRole;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
}
