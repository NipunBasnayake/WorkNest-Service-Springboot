package com.worknest.tenant.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamChatCreateRequestDto {

    @NotNull(message = "Team ID is required")
    @Positive(message = "Team ID must be positive")
    private Long teamId;
}
