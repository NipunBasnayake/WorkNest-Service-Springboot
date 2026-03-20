package com.worknest.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForceResetPasswordResponseDto {
    private Long userId;
    private String email;
    private boolean passwordChangeRequired;
}
