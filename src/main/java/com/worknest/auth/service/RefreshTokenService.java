package com.worknest.auth.service;

import com.worknest.auth.model.AuthSessionContext;
import com.worknest.master.entity.PlatformUser;
import com.worknest.master.entity.RefreshToken;

import java.util.List;

public interface RefreshTokenService {

    RefreshToken createToken(PlatformUser platformUser, AuthSessionContext sessionContext);

    RefreshToken validateToken(String tokenValue);

    RefreshToken validateTokenForRefresh(String tokenValue);

    RefreshToken rotateToken(RefreshToken currentToken, AuthSessionContext sessionContext);

    void revokeToken(String tokenValue);

    void revokeToken(Long tokenId);

    void revokeAllActiveTokens(PlatformUser platformUser);

    List<RefreshToken> getActiveTokens(PlatformUser platformUser);
}
