package com.worknest.auth.service;

import com.worknest.auth.dto.*;

public interface AuthService {

    LoginResponseDto login(LoginRequestDto requestDto);

    RefreshTokenResponseDto refreshAccessToken(RefreshTokenRequestDto requestDto);

    LogoutResponseDto logout(LogoutRequestDto requestDto);

    AuthUserDto getCurrentUser();
}
