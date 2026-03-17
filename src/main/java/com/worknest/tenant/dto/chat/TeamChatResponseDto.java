package com.worknest.tenant.dto.chat;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TeamChatResponseDto {
    private Long id;
    private Long teamId;
    private String teamName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
