package com.worknest.tenant.dto.team;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class TeamDetailResponseDto {
    private TeamResponseDto team;
    private List<TeamMemberResponseDto> members;
}
