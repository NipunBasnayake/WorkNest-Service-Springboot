package com.worknest.security.jwt;

import com.worknest.common.enums.PlatformRole;
import com.worknest.master.entity.PlatformUser;
import com.worknest.security.model.PlatformUserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final long MIN_ACCESS_TOKEN_EXPIRY_MILLIS = 60_000L;

    private final String jwtSecret;
    private final long accessTokenExpiryMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration:0}") long configuredAccessTokenExpiryMillis,
            @Value("${app.jwt.access-token-expiration-minutes:15}") long legacyAccessTokenExpiryMinutes) {
        this.jwtSecret = jwtSecret;
        this.accessTokenExpiryMillis = resolveAccessTokenExpiryMillis(
                configuredAccessTokenExpiryMillis, legacyAccessTokenExpiryMinutes);
    }

    public String generateAccessToken(PlatformUser platformUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", platformUser.getId());
        claims.put("role", platformUser.getRole().name());
        claims.put("tenantKey", platformUser.getTenantKey());
        return buildToken(claims, platformUser.getEmail());
    }

    public String generateAccessToken(PlatformUserPrincipal principal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", principal.getId());
        claims.put("role", principal.getRole().name());
        claims.put("tenantKey", principal.getTenantKey());
        return buildToken(claims, principal.getUsername());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTenantKey(String token) {
        return extractAllClaims(token).get("tenantKey", String.class);
    }

    public PlatformRole extractRole(String token) {
        String role = extractAllClaims(token).get("role", String.class);
        return role == null ? null : PlatformRole.valueOf(role);
    }

    public LocalDateTime getAccessTokenExpiryTime() {
        return LocalDateTime.now().plus(Duration.ofMillis(accessTokenExpiryMillis));
    }

    public LocalDateTime getTokenExpiryTime(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault());
    }

    public boolean isTokenValid(String token, PlatformUserPrincipal principal) {
        String username = extractUsername(token);
        return username != null
                && username.equalsIgnoreCase(principal.getUsername())
                && !isTokenExpired(token);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private String buildToken(Map<String, Object> extraClaims, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiryMillis);

        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private long resolveAccessTokenExpiryMillis(
            long configuredAccessTokenExpiryMillis,
            long legacyAccessTokenExpiryMinutes) {
        if (configuredAccessTokenExpiryMillis > 0) {
            return configuredAccessTokenExpiryMillis;
        }
        long resolvedLegacyMinutes = Math.max(1, legacyAccessTokenExpiryMinutes);
        long legacyMillis = Duration.ofMinutes(resolvedLegacyMinutes).toMillis();
        return Math.max(legacyMillis, MIN_ACCESS_TOKEN_EXPIRY_MILLIS);
    }
}
