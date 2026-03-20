package com.worknest.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResetPasswordRequestDto {

    @NotBlank(message = "Reset token is required")
    @Size(max = 512, message = "Reset token must not exceed 512 characters")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String confirmPassword;
}
