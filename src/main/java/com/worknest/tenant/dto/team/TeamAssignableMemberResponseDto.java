package com.worknest.tenant.dto.team;

import com.worknest.tenant.enums.TeamFunctionalRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TeamAssignableMemberResponseDto {
    private Long employeeId;
    private String fullName;
    private String email;
    private String designation;
    private String jobTitle;
    private String avatar;
    private TeamFunctionalRole teamRole;
    private boolean active;
}
