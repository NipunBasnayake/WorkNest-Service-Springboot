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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final long MIN_REFRESH_TOKEN_EXPIRY_MILLIS = 60_000L;

    private final RefreshTokenRepository refreshTokenRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final long refreshTokenExpiryMillis;
    private final long refreshRotationGraceMillis;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            MasterTenantContextRunner masterTenantContextRunner,
            @Value("${app.jwt.refresh-expiration:0}") long configuredRefreshTokenExpiryMillis,
            @Value("${app.jwt.refresh-token-expiration-days:7}") long legacyRefreshTokenExpiryDays,
            @Value("${app.jwt.refresh-rotation-grace-ms:45000}") long refreshRotationGraceMillis) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.refreshTokenExpiryMillis = resolveRefreshTokenExpiryMillis(
                configuredRefreshTokenExpiryMillis, legacyRefreshTokenExpiryDays);
        this.refreshRotationGraceMillis = Math.max(0, refreshRotationGraceMillis);
    }

    @Override
    public RefreshToken createToken(PlatformUser platformUser) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken refreshToken = buildToken(platformUser, generateTokenValue(), now);
        return masterTenantContextRunner.runInMasterContext(() -> refreshTokenRepository.save(refreshToken));
    }

    @Override
    public RefreshToken validateToken(String tokenValue) {
        RefreshToken refreshToken = findTokenOrThrow(tokenValue);
        validateNotExpired(refreshToken, tokenValue);

        if (refreshToken.isRevoked()) {
            throw new TokenRevokedException("Refresh token has been revoked");
        }

        return refreshToken;
    }

    @Override
    public RefreshToken validateTokenForRefresh(String tokenValue) {
        RefreshToken refreshToken = findTokenOrThrow(tokenValue);
        validateNotExpired(refreshToken, tokenValue);

        if (!refreshToken.isRevoked()) {
            return refreshToken;
        }

        if (isWithinRotationGrace(refreshToken)) {
            // Ensure a valid replacement exists before allowing refresh to continue.
            resolveRotatedToken(refreshToken);
            return refreshToken;
        }

        throw new TokenRevokedException("Refresh token has already been rotated or revoked");
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public RefreshToken rotateToken(RefreshToken currentToken) {
        validateNotExpired(currentToken, currentToken.getToken());

        LocalDateTime now = LocalDateTime.now();
        String rotatedToToken = generateTokenValue();
        int updatedRows = masterTenantContextRunner.runInMasterContext(() ->
                refreshTokenRepository.rotateIfActiveAndNotExpired(
                        currentToken.getToken(),
                        now,
                        now,
                        rotatedToToken));

        if (updatedRows == 1) {
            RefreshToken newToken = buildToken(currentToken.getPlatformUser(), rotatedToToken, now);
            return masterTenantContextRunner.runInMasterContext(() -> refreshTokenRepository.save(newToken));
        }

        RefreshToken latestState = findTokenOrThrow(currentToken.getToken());
        validateNotExpired(latestState, currentToken.getToken());

        if (latestState.isRevoked() && isWithinRotationGrace(latestState)) {
            return resolveRotatedToken(latestState);
        }

        if (latestState.isRevoked()) {
            throw new TokenRevokedException("Refresh token has already been rotated or revoked");
        }

        throw new InvalidTokenException("Refresh token cannot be rotated");
    }

    @Override
    public void revokeToken(String tokenValue) {
        masterTenantContextRunner.runInMasterContext(() -> {
            RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
                    .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));
            if (!refreshToken.isRevoked()) {
                refreshToken.setRevoked(true);
                refreshToken.setRevokedAt(LocalDateTime.now());
                refreshToken.setRotatedToToken(null);
                refreshTokenRepository.save(refreshToken);
            }
        });
    }

    @Override
    public void revokeAllActiveTokens(PlatformUser platformUser) {
        masterTenantContextRunner.runInMasterContext(() -> {
            List<RefreshToken> activeTokens = refreshTokenRepository
                    .findByPlatformUserAndRevokedFalseAndExpiresAtAfter(platformUser, LocalDateTime.now());
            LocalDateTime now = LocalDateTime.now();
            for (RefreshToken token : activeTokens) {
                token.setRevoked(true);
                token.setRevokedAt(now);
                token.setRotatedToToken(null);
            }
            refreshTokenRepository.saveAll(activeTokens);
        });
    }

    private RefreshToken buildToken(PlatformUser platformUser, String tokenValue, LocalDateTime issuedAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(tokenValue);
        refreshToken.setPlatformUser(platformUser);
        refreshToken.setRevoked(false);
        refreshToken.setRevokedAt(null);
        refreshToken.setRotatedToToken(null);
        refreshToken.setExpiresAt(issuedAt.plus(Duration.ofMillis(refreshTokenExpiryMillis)));
        return refreshToken;
    }

    private RefreshToken findTokenOrThrow(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new InvalidTokenException("Refresh token is invalid");
        }
        return masterTenantContextRunner.runInMasterContext(() ->
                refreshTokenRepository.findByToken(tokenValue)
                        .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid")));
    }

    private void validateNotExpired(RefreshToken refreshToken, String tokenValue) {
        if (refreshToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            return;
        }
        revokeToken(tokenValue);
        throw new TokenExpiredException("Refresh token has expired");
    }

    private boolean isWithinRotationGrace(RefreshToken refreshToken) {
        if (!refreshToken.isRevoked() || refreshToken.getRevokedAt() == null) {
            return false;
        }
        if (refreshToken.getRotatedToToken() == null || refreshToken.getRotatedToToken().isBlank()) {
            return false;
        }
        if (refreshRotationGraceMillis <= 0) {
            return false;
        }
        LocalDateTime graceDeadline = refreshToken.getRevokedAt()
                .plus(Duration.ofMillis(refreshRotationGraceMillis));
        return !graceDeadline.isBefore(LocalDateTime.now());
    }

    private RefreshToken resolveRotatedToken(RefreshToken revokedToken) {
        String rotatedToTokenValue = revokedToken.getRotatedToToken();
        if (rotatedToTokenValue == null || rotatedToTokenValue.isBlank()) {
            throw new TokenRevokedException("Refresh token has already been rotated or revoked");
        }

        RefreshToken refreshToken = masterTenantContextRunner.runInMasterContext(() ->
                refreshTokenRepository.findByTokenAndRevokedFalse(rotatedToTokenValue)
                        .orElseThrow(() -> new TokenRevokedException("Rotated refresh token is no longer active")));
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            revokeToken(rotatedToTokenValue);
            throw new TokenExpiredException("Refresh token has expired");
        }

        return refreshToken;
    }

    private long resolveRefreshTokenExpiryMillis(
            long configuredRefreshTokenExpiryMillis,
            long legacyRefreshTokenExpiryDays) {
        if (configuredRefreshTokenExpiryMillis > 0) {
            return configuredRefreshTokenExpiryMillis;
        }
        long resolvedLegacyDays = Math.max(1, legacyRefreshTokenExpiryDays);
        long legacyMillis = Duration.ofDays(resolvedLegacyDays).toMillis();
        return Math.max(legacyMillis, MIN_REFRESH_TOKEN_EXPIRY_MILLIS);
    }

    private String generateTokenValue() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }
}
