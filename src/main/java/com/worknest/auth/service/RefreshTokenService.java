package com.worknest.auth.service;

import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.RefreshToken;

public interface RefreshTokenService {

    RefreshToken createToken(PlatformUser platformUser);

    RefreshToken validateToken(String tokenValue);

    RefreshToken validateTokenForRefresh(String tokenValue);

    RefreshToken rotateToken(RefreshToken currentToken);

    void revokeToken(String tokenValue);

    void revokeAllActiveTokens(PlatformUser platformUser);
}
