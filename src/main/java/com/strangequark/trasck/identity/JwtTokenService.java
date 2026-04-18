package com.strangequark.trasck.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final Duration accessTokenTtl;

    public JwtTokenService(
            @Value("${trasck.security.jwt-secret:dev-only-change-me-dev-only-change-me-dev-only-change-me-32}") String jwtSecret,
            @Value("${trasck.security.access-token-ttl:PT8H}") String accessTokenTtl
    ) {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("trasck.security.jwt-secret must be at least 32 bytes for HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenTtl = Duration.parse(accessTokenTtl);
    }

    public AuthToken issue(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("typ", "access")
                .claim("email", user.getEmail())
                .claim("username", user.getUsername())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
        return new AuthToken(token, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    public UUID parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | JwtException ex) {
            throw new BadCredentialsException("Invalid access token", ex);
        }
    }

    public record AuthToken(String value, OffsetDateTime expiresAt) {
    }
}
