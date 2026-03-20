package com.worknest.auth.controller;

import com.worknest.auth.dto.*;
import com.worknest.auth.service.AuthService;
import com.worknest.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(requestDto)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponseDto>> refreshToken(
            @Valid @RequestBody RefreshTokenRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success(
                "Token refreshed successfully",
                authService.refreshAccessToken(requestDto)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponseDto>> logout(
            @Valid @RequestBody LogoutRequestDto requestDto) {
        return ResponseEntity.ok(ApiResponse.success("Logout successful", authService.logout(requestDto)));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto requestDto) {
        authService.forgotPassword(requestDto);
        return ResponseEntity.ok(ApiResponse.success(
                "If an account exists for the provided email, a reset link has been sent."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto requestDto) {
        authService.resetPassword(requestDto);
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDto requestDto) {
        authService.changePassword(requestDto);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully."));
    }

    @PostMapping("/admin/users/{id}/force-reset-password")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','HR')")
    public ResponseEntity<ApiResponse<ForceResetPasswordResponseDto>> forceResetPassword(@PathVariable Long id) {
        ForceResetPasswordResponseDto response = authService.forceResetPassword(id);
        return ResponseEntity.ok(ApiResponse.success("Temporary password sent to user email.", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserDto>> getCurrentUser() {
        return ResponseEntity.ok(ApiResponse.success("Current user retrieved", authService.getCurrentUser()));
    }
}
