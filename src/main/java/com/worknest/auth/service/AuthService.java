package com.worknest.auth.service;

import com.worknest.auth.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    LoginResponseDto login(LoginRequestDto requestDto, HttpServletRequest request, HttpServletResponse response);

    RefreshTokenResponseDto refreshAccessToken(
            RefreshTokenRequestDto requestDto,
            HttpServletRequest request,
            HttpServletResponse response);

    LogoutResponseDto logout(LogoutRequestDto requestDto, HttpServletRequest request, HttpServletResponse response);

    AuthUserDto getCurrentUser();

    AuthSessionsResponseDto getActiveSessions(HttpServletRequest request);

    AuthSessionRevocationResponseDto revokeSession(Long sessionId, HttpServletRequest request);

    AuthSessionRevocationResponseDto revokeOtherSessions(HttpServletRequest request);

    void forgotPassword(ForgotPasswordRequestDto requestDto);

    void resetPassword(ResetPasswordRequestDto requestDto);

    void changePassword(ChangePasswordRequestDto requestDto);

    ForceResetPasswordResponseDto forceResetPassword(Long userId);
}
