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

    List<RefreshToken> findByPlatformUserAndRevokedFalseAndExpiresAtAfter(
            PlatformUser platformUser, LocalDateTime currentTime);
}
