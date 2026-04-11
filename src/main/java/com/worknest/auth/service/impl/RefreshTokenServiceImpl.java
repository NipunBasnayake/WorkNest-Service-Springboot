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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.HexFormat;

@Service
@Transactional(transactionManager = "masterTransactionManager")
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final long MIN_REFRESH_TOKEN_EXPIRY_MILLIS = 60_000L;

    private final RefreshTokenRepository refreshTokenRepository;
    private final MasterTenantContextRunner masterTenantContextRunner;
    private final long refreshTokenExpiryMillis;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            MasterTenantContextRunner masterTenantContextRunner,
            @Value("${app.jwt.refresh-expiration:0}") long configuredRefreshTokenExpiryMillis,
            @Value("${app.jwt.refresh-token-expiration-days:7}") long legacyRefreshTokenExpiryDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.masterTenantContextRunner = masterTenantContextRunner;
        this.refreshTokenExpiryMillis = resolveRefreshTokenExpiryMillis(
                configuredRefreshTokenExpiryMillis, legacyRefreshTokenExpiryDays);
    }

    @Override
    public RefreshToken createToken(PlatformUser platformUser) {
        LocalDateTime now = LocalDateTime.now();
        String rawToken = generateTokenValue();
        RefreshToken refreshToken = buildToken(platformUser, rawToken, now);
        refreshToken.setRawToken(rawToken);
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

        throw new TokenRevokedException("Refresh token has already been rotated or revoked");
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public RefreshToken rotateToken(RefreshToken currentToken) {
        validateNotExpired(currentToken, currentToken.getRawToken());

        LocalDateTime now = LocalDateTime.now();
        String rotatedToToken = generateTokenValue();
        String rotatedToTokenHash = hashToken(rotatedToToken);
        int updatedRows = masterTenantContextRunner.runInMasterContext(() ->
                refreshTokenRepository.rotateIfActiveAndNotExpired(
                        currentToken.getTokenHash(),
                        now,
                        now,
                        rotatedToTokenHash));

        if (updatedRows == 1) {
            RefreshToken newToken = buildToken(currentToken.getPlatformUser(), rotatedToToken, now);
            newToken.setRawToken(rotatedToToken);
            return masterTenantContextRunner.runInMasterContext(() -> refreshTokenRepository.save(newToken));
        }

        RefreshToken latestState = findTokenOrThrow(currentToken.getRawToken());
        validateNotExpired(latestState, currentToken.getRawToken());

        if (latestState.isRevoked()) {
            throw new TokenRevokedException("Refresh token has already been rotated or revoked");
        }

        throw new InvalidTokenException("Refresh token cannot be rotated");
    }

    @Override
    public void revokeToken(String tokenValue) {
        masterTenantContextRunner.runInMasterContext(() -> {
            RefreshToken refreshToken = findTokenOrThrow(tokenValue);
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
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setTokenHash(hashToken(tokenValue));
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
        String normalizedToken = tokenValue.trim();
        String tokenHash = hashToken(normalizedToken);
        return masterTenantContextRunner.runInMasterContext(() ->
                refreshTokenRepository.findByTokenHash(tokenHash)
                        .or(() -> refreshTokenRepository.findByToken(normalizedToken))
                        .map(token -> attachRawToken(token, normalizedToken))
                        .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid")));
    }

    private void validateNotExpired(RefreshToken refreshToken, String tokenValue) {
        if (refreshToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            return;
        }
        revokeToken(tokenValue);
        throw new TokenExpiredException("Refresh token has expired");
    }

    private RefreshToken attachRawToken(RefreshToken refreshToken, String rawToken) {
        refreshToken.setRawToken(rawToken);
        if (refreshToken.getTokenHash() == null || refreshToken.getTokenHash().isBlank()) {
            refreshToken.setTokenHash(hashToken(rawToken));
            refreshTokenRepository.save(refreshToken);
        }
        return refreshToken;
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
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
