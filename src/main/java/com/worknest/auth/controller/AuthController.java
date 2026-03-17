package com.worknest.auth.controller;

import com.worknest.auth.dto.*;
import com.worknest.auth.service.AuthService;
import com.worknest.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserDto>> getCurrentUser() {
        return ResponseEntity.ok(ApiResponse.success("Current user retrieved", authService.getCurrentUser()));
    }
}
