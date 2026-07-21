package com.worknest.auth.service.impl;

import com.worknest.auth.dto.ChangePasswordRequestDto;
import com.worknest.auth.dto.LoginResponseDto;
import com.worknest.auth.model.AuthSessionContext;
import com.worknest.auth.service.AuthCookieService;
import com.worknest.auth.service.AuthLoginThrottleService;
import com.worknest.auth.service.PlatformUserService;
import com.worknest.auth.service.RefreshTokenService;
import com.worknest.common.enums.PlatformRole;
import com.worknest.common.enums.UserStatus;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.RefreshToken;
import com.worknest.master.repository.PlatformUserRepository;
import com.worknest.master.repository.RefreshTokenRepository;
import com.worknest.master.service.MasterTenantLookupService;
import com.worknest.notification.email.EmailNotificationService;
import com.worknest.security.jwt.JwtService;
import com.worknest.security.model.PlatformUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private PlatformUserService platformUserService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuthCookieService authCookieService;
    @Mock private AuthLoginThrottleService authLoginThrottleService;
    @Mock private JwtService jwtService;
    @Mock private MasterTenantLookupService masterTenantLookupService;
    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private EmailNotificationService emailNotificationService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthServiceImpl(
                platformUserService,
                refreshTokenService,
                authCookieService,
                authLoginThrottleService,
                passwordEncoder,
                jwtService,
                masterTenantLookupService,
                platformUserRepository,
                refreshTokenRepository,
                emailNotificationService,
                "X-Tenant-Slug",
                20,
                "http://localhost:5173/reset-password");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiredPasswordChangePersistsPasswordAndReturnsReplacementSession() {
        String oldPassword = "ChangeMe123!";
        String newPassword = "NewSecure456!";
        PlatformUser user = platformAdmin(oldPassword);
        PlatformUserPrincipal principal = new PlatformUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        ChangePasswordRequestDto requestDto = new ChangePasswordRequestDto();
        requestDto.setCurrentPassword(oldPassword);
        requestDto.setNewPassword(newPassword);
        requestDto.setConfirmPassword(newPassword);

        RefreshToken replacementRefreshToken = new RefreshToken();
        replacementRefreshToken.setId(22L);
        replacementRefreshToken.setRawToken("replacement-refresh-token");
        replacementRefreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(platformUserRepository.saveAndFlush(user)).thenReturn(user);
        when(refreshTokenService.createToken(any(PlatformUser.class), any(AuthSessionContext.class)))
                .thenReturn(replacementRefreshToken);
        when(jwtService.generateAccessToken(user)).thenReturn("replacement-access-token");
        when(jwtService.getAccessTokenExpiryTime()).thenReturn(LocalDateTime.now().plusMinutes(15));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        LoginResponseDto result = authService.changeRequiredPassword(requestDto, request, response);

        assertThat(user.isPasswordChangeRequired()).isFalse();
        assertThat(passwordEncoder.matches(newPassword, user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(oldPassword, user.getPasswordHash())).isFalse();
        assertThat(result.isPasswordChangeRequired()).isFalse();
        assertThat(result.getAccessToken()).isEqualTo("replacement-access-token");
        assertThat(result.getRefreshToken()).isEqualTo("replacement-refresh-token");
        assertThat(result.getSessionId()).isEqualTo(22L);

        InOrder persistenceOrder = inOrder(platformUserRepository, refreshTokenService);
        persistenceOrder.verify(platformUserRepository).saveAndFlush(user);
        persistenceOrder.verify(refreshTokenService).revokeAllActiveTokens(user);
        persistenceOrder.verify(refreshTokenService).createToken(any(PlatformUser.class), any(AuthSessionContext.class));
        verify(authCookieService).issueAuthCookies(
                org.mockito.ArgumentMatchers.eq(response),
                org.mockito.ArgumentMatchers.eq("replacement-refresh-token"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.longThat(value -> value > 0));
    }

    private PlatformUser platformAdmin(String password) {
        PlatformUser user = new PlatformUser();
        user.setId(1L);
        user.setFullName("Platform Admin");
        user.setEmail("platform.admin@worknest.local");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(PlatformRole.PLATFORM_ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordChangeRequired(true);
        return user;
    }
}
