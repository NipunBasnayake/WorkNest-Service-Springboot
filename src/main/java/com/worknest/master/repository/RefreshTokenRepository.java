package com.worknest.master.repository;

import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.RefreshToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

        List<RefreshToken> findByPlatformUserOrderByCreatedAtDesc(PlatformUser platformUser);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true, rt.revokedAt = :revokedAt
            WHERE rt.token = :token
              AND rt.revoked = false
              AND rt.expiresAt > :now
            """)
    int revokeIfActiveAndNotExpired(
            @Param("token") String token,
            @Param("now") LocalDateTime now,
            @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true, rt.revokedAt = :revokedAt, rt.rotatedToToken = :rotatedToToken
            WHERE rt.tokenHash = :tokenHash
              AND rt.revoked = false
              AND rt.expiresAt > :now
            """)
    int rotateIfActiveAndNotExpired(
            @Param("tokenHash") String tokenHash,
            @Param("now") LocalDateTime now,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("rotatedToToken") String rotatedToToken);

    List<RefreshToken> findByPlatformUserAndRevokedFalseAndExpiresAtAfter(
            PlatformUser platformUser, LocalDateTime currentTime);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.lastUsedAt = :lastUsedAt,
                rt.ipAddress = COALESCE(:ipAddress, rt.ipAddress),
                rt.userAgent = COALESCE(:userAgent, rt.userAgent),
                rt.deviceId = COALESCE(:deviceId, rt.deviceId),
                rt.deviceName = COALESCE(:deviceName, rt.deviceName)
            WHERE rt.id = :id
            """)
    int updateSessionContext(
            @Param("id") Long id,
            @Param("lastUsedAt") LocalDateTime lastUsedAt,
            @Param("ipAddress") String ipAddress,
            @Param("userAgent") String userAgent,
            @Param("deviceId") String deviceId,
            @Param("deviceName") String deviceName);
}
