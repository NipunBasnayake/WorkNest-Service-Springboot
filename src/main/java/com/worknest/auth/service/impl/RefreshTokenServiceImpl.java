package com.worknest.auth.service.impl;

import com.worknest.auth.service.RefreshTokenService;
import com.worknest.common.exception.InvalidTokenException;
import com.worknest.common.exception.TokenExpiredException;
import com.worknest.common.exception.TokenRevokedException;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.RefreshToken;
import com.worknest.master.repository.RefreshTokenRepository;
import com.worknest.tenant.context.MasterTenantContextRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final long refreshTokenExpiryDays;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            MasterTenantContextRunner masterTenantContextRunner,
            @Value("${app.jwt.refresh-token-expiration-days:7}") long refreshTokenExpiryDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
    }

    @Override
    public RefreshToken createToken(PlatformUser platformUser) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(generateTokenValue());
        refreshToken.setPlatformUser(platformUser);
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays));
        return masterTenantContextRunner.runInMasterContext(() -> refreshTokenRepository.save(refreshToken));
    }

    @Override
    public RefreshToken validateToken(String tokenValue) {
        RefreshToken refreshToken = masterTenantContextRunner.runInMasterContext(() ->
                refreshTokenRepository.findByToken(tokenValue)
                        .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid")));

        if (refreshToken.isRevoked()) {
            throw new TokenRevokedException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            revokeToken(tokenValue);
            throw new TokenExpiredException("Refresh token has expired");
        }

        return refreshToken;
    }

    @Override
    public RefreshToken rotateToken(RefreshToken currentToken) {
        revokeToken(currentToken.getToken());
        return createToken(currentToken.getPlatformUser());
    }

    @Override
    public void revokeToken(String tokenValue) {
        masterTenantContextRunner.runInMasterContext(() -> {
            RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
                    .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));
            if (!refreshToken.isRevoked()) {
                refreshToken.setRevoked(true);
                refreshToken.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(refreshToken);
            }
        });
    }

    @Override
    public void revokeAllActiveTokens(PlatformUser platformUser) {
        masterTenantContextRunner.runInMasterContext(() -> {
            List<RefreshToken> activeTokens = refreshTokenRepository
                    .findByPlatformUserAndRevokedFalseAndExpiresAtAfter(platformUser, LocalDateTime.now());
            for (RefreshToken token : activeTokens) {
                token.setRevoked(true);
                token.setRevokedAt(LocalDateTime.now());
            }
            refreshTokenRepository.saveAll(activeTokens);
        });
    }

    private String generateTokenValue() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }
}
