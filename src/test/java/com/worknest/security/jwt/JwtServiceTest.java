package com.worknest.security.jwt;

import com.worknest.common.enums.PlatformRole;
import com.worknest.master.entity.PlatformUser;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET =
            "d29ya25lc3QtdGVzdC1qd3Qtc2VjcmV0LXRoYXQtaXMtYXQtbGVhc3QtMzItYnl0ZXM=";

    @Test
    void generateAccessTokenCreatesUniqueTokensWithinTheSameSecond() {
        JwtService jwtService = new JwtService(SECRET, 900_000L, 15L);
        PlatformUser user = new PlatformUser();
        user.setId(1L);
        user.setEmail("platform.admin@worknest.local");
        user.setRole(PlatformRole.PLATFORM_ADMIN);

        String firstToken = jwtService.generateAccessToken(user);
        String secondToken = jwtService.generateAccessToken(user);

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(jwtService.extractClaim(firstToken, Claims::getId)).isNotBlank();
        assertThat(jwtService.extractClaim(secondToken, Claims::getId)).isNotBlank();
    }
}
