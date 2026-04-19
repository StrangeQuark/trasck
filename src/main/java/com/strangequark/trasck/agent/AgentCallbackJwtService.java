package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentCallbackJwtService {

    public static final String CALLBACK_HEADER = "X-Trasck-Agent-Callback-Jwt";

    private static final String CALLBACK_JWT_CONFIG = "callbackJwt";
    private static final String CALLBACK_PRIVATE_KEY_CREDENTIAL_TYPE = "callback_private_key";

    private final ObjectMapper objectMapper;
    private final AgentProviderCredentialRepository agentProviderCredentialRepository;
    private final SecretCipherService secretCipherService;
    private final Duration ttl;

    public AgentCallbackJwtService(
            ObjectMapper objectMapper,
            AgentProviderCredentialRepository agentProviderCredentialRepository,
            SecretCipherService secretCipherService,
            @Value("${trasck.agents.callback-jwt-ttl:PT15M}") String callbackJwtTtl
    ) {
        this.objectMapper = objectMapper;
        this.agentProviderCredentialRepository = agentProviderCredentialRepository;
        this.secretCipherService = secretCipherService;
        this.ttl = Duration.parse(callbackJwtTtl);
    }

    public JsonNode ensureProviderKeyPair(AgentProvider provider) {
        if (provider.getId() == null) {
            throw new IllegalStateException("Agent provider must be persisted before callback key generation");
        }
        ObjectNode config = objectConfig(provider.getConfig());
        JsonNode callbackConfig = config.path(CALLBACK_JWT_CONFIG);
        CallbackKeyMaterial existingKey = callbackConfig.isObject() && callbackConfig.path("currentKid").isTextual()
                ? activeKeyOrNull(callbackConfig)
                : null;
        if (existingKey != null && hasPrivateKeyCredential(provider, existingKey.keyId())) {
            return config;
        }

        CallbackKeyMaterial generated = generateKeyMaterial();
        persistPrivateKeyCredential(provider, generated);
        ObjectNode callbackJwt = objectMapper.createObjectNode()
                .put("algorithm", "RS256")
                .put("currentKid", generated.keyId());
        ArrayNode keys = objectMapper.createArrayNode();
        keys.add(objectMapper.createObjectNode()
                .put("kid", generated.keyId())
                .put("alg", "RS256")
                .put("createdAt", Instant.now().toString())
                .set("publicJwk", generated.publicJwk()));
        callbackJwt.set("keys", keys);
        config.set(CALLBACK_JWT_CONFIG, callbackJwt);
        return config;
    }

    public String issue(AgentTask task, AgentProvider provider, AgentProfile profile) {
        CallbackKeyMaterial key = activeKey(objectConfig(provider.getConfig()).path(CALLBACK_JWT_CONFIG));
        String privateKeyPem = privateKeyPem(provider, key.keyId());
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .issuer(provider.getProviderKey())
                .subject(task.getId().toString())
                .claim("typ", "agent_callback")
                .claim("kid", key.keyId())
                .claim("workspace_id", task.getWorkspaceId().toString())
                .claim("provider_id", provider.getId().toString())
                .claim("provider_key", provider.getProviderKey())
                .claim("agent_profile_id", profile.getId().toString())
                .claim("external_task_id", task.getExternalTaskId())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ttl)))
                .signWith(privateKey(privateKeyPem))
                .compact();
    }

    public AgentCallbackClaims peek(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized("Missing agent callback assertion");
        }
        try {
            return claimsFromJson(unsignedPayload(token));
        } catch (IllegalArgumentException ex) {
            throw unauthorized("Invalid agent callback assertion");
        }
    }

    public AgentCallbackClaims parse(AgentProvider provider, String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized("Missing agent callback assertion");
        }
        AgentCallbackClaims untrusted = peek(token);
        try {
            CallbackKeyMaterial key = key(objectConfig(provider.getConfig()).path(CALLBACK_JWT_CONFIG), untrusted.keyId());
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey(key.publicJwk()))
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
            return claimsFromJwt(claims);
        } catch (IllegalArgumentException | IllegalStateException | JwtException ex) {
            throw unauthorized("Invalid agent callback assertion");
        }
    }

    private AgentCallbackClaims claimsFromJwt(Claims claims) {
        if (!"agent_callback".equals(claims.get("typ", String.class))) {
            throw unauthorized("Invalid agent callback assertion type");
        }
        return new AgentCallbackClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("workspace_id", String.class)),
                UUID.fromString(claims.get("provider_id", String.class)),
                claims.get("provider_key", String.class),
                UUID.fromString(claims.get("agent_profile_id", String.class)),
                claims.getId(),
                claims.get("kid", String.class)
        );
    }

    private AgentCallbackClaims claimsFromJson(JsonNode claims) {
        if (!"agent_callback".equals(claims.path("typ").asText())) {
            throw unauthorized("Invalid agent callback assertion type");
        }
        return new AgentCallbackClaims(
                UUID.fromString(claims.path("sub").asText()),
                UUID.fromString(claims.path("workspace_id").asText()),
                UUID.fromString(claims.path("provider_id").asText()),
                claims.path("provider_key").asText(),
                UUID.fromString(claims.path("agent_profile_id").asText()),
                claims.path("jti").asText(null),
                claims.path("kid").asText(null)
        );
    }

    private JsonNode unsignedPayload(String token) {
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT must have three parts");
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JWT payload is not valid JSON", ex);
        }
    }

    private ObjectNode objectConfig(JsonNode config) {
        if (config != null && config.isObject()) {
            return (ObjectNode) config.deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private CallbackKeyMaterial activeKey(JsonNode callbackConfig) {
        return key(callbackConfig, callbackConfig.path("currentKid").asText(null));
    }

    private CallbackKeyMaterial activeKeyOrNull(JsonNode callbackConfig) {
        try {
            return activeKey(callbackConfig);
        } catch (ResponseStatusException ex) {
            return null;
        }
    }

    private CallbackKeyMaterial key(JsonNode callbackConfig, String keyId) {
        if (keyId == null || keyId.isBlank() || !callbackConfig.path("keys").isArray()) {
            throw unauthorized("Agent provider callback key is missing");
        }
        for (JsonNode key : callbackConfig.path("keys")) {
            if (keyId.equals(key.path("kid").asText())) {
                return new CallbackKeyMaterial(
                        keyId,
                        null,
                        key.path("publicJwk").deepCopy()
                );
            }
        }
        throw unauthorized("Agent provider callback key is unknown");
    }

    private boolean hasPrivateKeyCredential(AgentProvider provider, String keyId) {
        return agentProviderCredentialRepository.findByProviderIdAndCredentialTypeAndActiveTrue(provider.getId(), CALLBACK_PRIVATE_KEY_CREDENTIAL_TYPE).stream()
                .anyMatch(credential -> credential.getMetadata() != null && keyId.equals(credential.getMetadata().path("kid").asText()));
    }

    private void persistPrivateKeyCredential(AgentProvider provider, CallbackKeyMaterial key) {
        agentProviderCredentialRepository.findByProviderIdAndCredentialTypeAndActiveTrue(provider.getId(), CALLBACK_PRIVATE_KEY_CREDENTIAL_TYPE)
                .forEach(existing -> {
                    existing.setActive(false);
                    existing.setRotatedAt(OffsetDateTime.now());
                });
        AgentProviderCredential credential = new AgentProviderCredential();
        credential.setProviderId(provider.getId());
        credential.setCredentialType(CALLBACK_PRIVATE_KEY_CREDENTIAL_TYPE);
        credential.setEncryptedSecret(secretCipherService.encrypt(key.privateKeyPem()));
        credential.setMetadata(objectMapper.createObjectNode()
                .put("kid", key.keyId())
                .put("alg", "RS256")
                .put("use", "agent_callback_jwt"));
        credential.setActive(true);
        agentProviderCredentialRepository.save(credential);
    }

    private String privateKeyPem(AgentProvider provider, String keyId) {
        return agentProviderCredentialRepository.findByProviderIdAndCredentialTypeAndActiveTrue(provider.getId(), CALLBACK_PRIVATE_KEY_CREDENTIAL_TYPE).stream()
                .filter(credential -> credential.getMetadata() != null && keyId.equals(credential.getMetadata().path("kid").asText()))
                .findFirst()
                .map(credential -> secretCipherService.decrypt(credential.getEncryptedSecret()))
                .orElseThrow(() -> new IllegalStateException("Agent provider callback JWT key material is missing"));
    }

    private CallbackKeyMaterial generateKeyMaterial() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            String keyId = UUID.randomUUID().toString();
            ObjectNode publicJwk = objectMapper.createObjectNode()
                    .put("kty", "RSA")
                    .put("use", "sig")
                    .put("alg", "RS256")
                    .put("kid", keyId)
                    .put("n", base64Url(unsigned(publicKey.getModulus())))
                    .put("e", base64Url(unsigned(publicKey.getPublicExponent())));
            return new CallbackKeyMaterial(keyId, privateKeyPem(keyPair.getPrivate()), publicJwk);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA is unavailable", ex);
        }
    }

    private PrivateKey privateKey(String pem) {
        try {
            String content = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(content);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Agent callback private key is invalid", ex);
        }
    }

    private PublicKey publicKey(JsonNode publicJwk) {
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(publicJwk.path("n").asText()));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(publicJwk.path("e").asText()));
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception ex) {
            throw new IllegalStateException("Agent callback public key is invalid", ex);
        }
    }

    private String privateKeyPem(PrivateKey privateKey) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----";
    }

    private byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private record CallbackKeyMaterial(String keyId, String privateKeyPem, JsonNode publicJwk) {
    }

    public record AgentCallbackClaims(
            UUID taskId,
            UUID workspaceId,
            UUID providerId,
            String providerKey,
            UUID agentProfileId,
            String jwtId,
            String keyId
    ) {
    }
}
