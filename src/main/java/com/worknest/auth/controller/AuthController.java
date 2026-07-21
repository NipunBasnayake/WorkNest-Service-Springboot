package com.worknest.auth.controller;

import com.worknest.auth.dto.*;
import com.worknest.auth.service.AuthService;
import com.worknest.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
            @Valid @RequestBody LoginRequestDto requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(requestDto, request, response)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponseDto>> refreshToken(
            @Valid @RequestBody(required = false) RefreshTokenRequestDto requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success(
                "Token refreshed successfully",
                authService.refreshAccessToken(requestDto == null ? new RefreshTokenRequestDto() : requestDto, request, response)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<LogoutResponseDto>> logout(
            @Valid @RequestBody(required = false) LogoutRequestDto requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success("Logout successful", authService.logout(requestDto == null ? new LogoutRequestDto() : requestDto, request, response)));
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

    @PostMapping("/change-password-required")
    public ResponseEntity<ApiResponse<LoginResponseDto>> changeRequiredPassword(
            @Valid @RequestBody ChangePasswordRequestDto requestDto,
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(ApiResponse.success(
                "Password changed successfully.",
                authService.changeRequiredPassword(requestDto, request, response)));
    }

    @PostMapping("/admin/users/{id}/force-reset-password")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','HR')")
    public ResponseEntity<ApiResponse<ForceResetPasswordResponseDto>> forceResetPassword(@PathVariable("id") Long id) {
        ForceResetPasswordResponseDto response = authService.forceResetPassword(id);
        return ResponseEntity.ok(ApiResponse.success("Temporary password sent to user email.", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserDto>> getCurrentUser() {
        return ResponseEntity.ok(ApiResponse.success("Current user retrieved", authService.getCurrentUser()));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<AuthSessionsResponseDto>> getActiveSessions(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Active sessions retrieved", authService.getActiveSessions(request)));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<AuthSessionRevocationResponseDto>> revokeSession(
            @PathVariable("id") Long id,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Session revoked", authService.revokeSession(id, request)));
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<ApiResponse<AuthSessionRevocationResponseDto>> revokeOtherSessions(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Other sessions revoked", authService.revokeOtherSessions(request)));
    }
}
