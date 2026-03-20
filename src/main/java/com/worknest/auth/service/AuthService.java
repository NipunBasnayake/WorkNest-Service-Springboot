package com.worknest.auth.service;

import com.worknest.auth.dto.*;

public interface AuthService {

    LoginResponseDto login(LoginRequestDto requestDto);

    RefreshTokenResponseDto refreshAccessToken(RefreshTokenRequestDto requestDto);

    LogoutResponseDto logout(LogoutRequestDto requestDto);

    AuthUserDto getCurrentUser();

    void forgotPassword(ForgotPasswordRequestDto requestDto);

    void resetPassword(ResetPasswordRequestDto requestDto);

    void changePassword(ChangePasswordRequestDto requestDto);

    ForceResetPasswordResponseDto forceResetPassword(Long userId);
}
