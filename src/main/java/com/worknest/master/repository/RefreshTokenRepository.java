package com.worknest.master.repository;

import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByPlatformUserAndRevokedFalseAndExpiresAtAfter(
            PlatformUser platformUser, LocalDateTime currentTime);
}
