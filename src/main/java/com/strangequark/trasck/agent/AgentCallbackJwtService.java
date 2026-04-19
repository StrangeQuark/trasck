package com.strangequark.trasck.agent;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentCallbackJwtService {

    public static final String CALLBACK_HEADER = "X-Trasck-Agent-Callback-Jwt";

    private final SecretKey secretKey;
    private final Duration ttl;

    public AgentCallbackJwtService(
            @Value("${trasck.agents.callback-jwt-secret:dev-only-change-me-agent-callback-secret-change-me-32}") String callbackJwtSecret,
            @Value("${trasck.agents.callback-jwt-ttl:PT15M}") String callbackJwtTtl
    ) {
        byte[] secretBytes = callbackJwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("trasck.agents.callback-jwt-secret must be at least 32 bytes for HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.ttl = Duration.parse(callbackJwtTtl);
    }

    public String issue(AgentTask task, AgentProvider provider, AgentProfile profile) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .issuer(provider.getProviderKey())
                .subject(task.getId().toString())
                .claim("typ", "agent_callback")
                .claim("workspace_id", task.getWorkspaceId().toString())
                .claim("provider_id", provider.getId().toString())
                .claim("provider_key", provider.getProviderKey())
                .claim("agent_profile_id", profile.getId().toString())
                .claim("external_task_id", task.getExternalTaskId())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ttl)))
                .signWith(secretKey)
                .compact();
    }

    public AgentCallbackClaims parse(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized("Missing agent callback assertion");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
            if (!"agent_callback".equals(claims.get("typ", String.class))) {
                throw unauthorized("Invalid agent callback assertion type");
            }
            return new AgentCallbackClaims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("workspace_id", String.class)),
                    UUID.fromString(claims.get("provider_id", String.class)),
                    claims.get("provider_key", String.class),
                    UUID.fromString(claims.get("agent_profile_id", String.class)),
                    claims.getId()
            );
        } catch (IllegalArgumentException | JwtException ex) {
            throw unauthorized("Invalid agent callback assertion");
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    public record AgentCallbackClaims(
            UUID taskId,
            UUID workspaceId,
            UUID providerId,
            String providerKey,
            UUID agentProfileId,
            String jwtId
    ) {
    }
}
