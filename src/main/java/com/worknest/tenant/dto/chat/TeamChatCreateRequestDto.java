package com.worknest.tenant.dto.chat;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamChatCreateRequestDto {

    @NotNull(message = "Team ID is required")
    private Long teamId;
}
