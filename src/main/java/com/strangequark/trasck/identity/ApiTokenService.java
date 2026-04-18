package com.strangequark.trasck.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.Role;
import com.strangequark.trasck.access.RoleRepository;
import com.strangequark.trasck.access.WorkspaceMembership;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.event.DomainEventService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApiTokenService {

    static final String PERSONAL_PREFIX = "trpat_";
    static final String SERVICE_PREFIX = "trsvc_";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiTokenRepository apiTokenRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final DomainEventService domainEventService;
    private final ObjectMapper objectMapper;

    public ApiTokenService(
            ApiTokenRepository apiTokenRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            DomainEventService domainEventService,
            ObjectMapper objectMapper
    ) {
        this.apiTokenRepository = apiTokenRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.domainEventService = domainEventService;
        this.objectMapper = objectMapper;
    }

    public boolean supports(String rawToken) {
        return rawToken != null && (rawToken.startsWith(PERSONAL_PREFIX) || rawToken.startsWith(SERVICE_PREFIX));
    }

    @Transactional
    public UUID authenticateBearerToken(String rawToken) {
        if (!supports(rawToken)) {
            throw new BadCredentialsException("Unsupported API token");
        }
        ApiToken token = apiTokenRepository.findByTokenHashAndRevokedAtIsNull(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid API token"));
        if (token.getExpiresAt() != null && !token.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new BadCredentialsException("Expired API token");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadCredentialsException("API token user not found"));
        if (!Boolean.TRUE.equals(user.getActive()) || user.getDeletedAt() != null) {
            throw new BadCredentialsException("API token user is inactive");
        }
        token.setLastUsedAt(OffsetDateTime.now());
        return user.getId();
    }

    @Transactional
    public ApiTokenResponse createPersonalToken(UUID userId, CreatePersonalTokenRequest request) {
        CreatePersonalTokenRequest createRequest = required(request, "request");
        User user = activeUser(userId);
        RawApiToken rawToken = newRawToken(PERSONAL_PREFIX);
        ApiToken token = new ApiToken();
        token.setUserId(user.getId());
        token.setTokenType("personal");
        token.setName(requiredText(createRequest.name(), "name"));
        token.setTokenPrefix(rawToken.prefix());
        token.setTokenHash(hash(rawToken.value()));
        token.setScopes(scopes(createRequest.scopes()));
        token.setCreatedById(user.getId());
        token.setExpiresAt(createRequest.expiresAt());
        ApiToken saved = apiTokenRepository.save(token);
        recordTokenEvent(null, saved, "auth.api_token_created");
        return ApiTokenResponse.from(saved, rawToken.value());
    }

    @Transactional
    public ApiTokenResponse createServiceToken(UUID workspaceId, UUID createdById, CreateServiceTokenRequest request) {
        CreateServiceTokenRequest createRequest = required(request, "request");
        Role role = resolveWorkspaceRole(workspaceId, createRequest.roleId());
        User creator = activeUser(createdById);
        User serviceUser = createServiceUser(createRequest);
        createWorkspaceMembership(workspaceId, serviceUser.getId(), role.getId());

        RawApiToken rawToken = newRawToken(SERVICE_PREFIX);
        ApiToken token = new ApiToken();
        token.setWorkspaceId(workspaceId);
        token.setUserId(serviceUser.getId());
        token.setTokenType("service");
        token.setName(requiredText(createRequest.name(), "name"));
        token.setTokenPrefix(rawToken.prefix());
        token.setTokenHash(hash(rawToken.value()));
        token.setRoleId(role.getId());
        token.setScopes(scopes(createRequest.scopes()));
        token.setCreatedById(creator.getId());
        token.setExpiresAt(createRequest.expiresAt());
        ApiToken saved = apiTokenRepository.save(token);
        recordTokenEvent(workspaceId, saved, "auth.service_token_created");
        return ApiTokenResponse.from(saved, rawToken.value());
    }

    @Transactional
    public void revokePersonalToken(UUID tokenId, UUID userId) {
        ApiToken token = apiTokenRepository.findByIdAndUserIdAndTokenTypeAndRevokedAtIsNull(tokenId, userId, "personal")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API token not found"));
        revoke(token, userId, "auth.api_token_revoked");
    }

    @Transactional
    public void revokeServiceToken(UUID workspaceId, UUID tokenId, UUID revokedById) {
        ApiToken token = apiTokenRepository.findByIdAndWorkspaceIdAndTokenTypeAndRevokedAtIsNull(tokenId, workspaceId, "service")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service token not found"));
        revoke(token, revokedById, "auth.service_token_revoked");
    }

    private void revoke(ApiToken token, UUID revokedById, String eventType) {
        token.setRevokedAt(OffsetDateTime.now());
        token.setRevokedById(revokedById);
        recordTokenEvent(token.getWorkspaceId(), token, eventType);
    }

    private User createServiceUser(CreateServiceTokenRequest request) {
        String displayName = firstText(request.displayName(), requiredText(request.name(), "name"));
        String usernameBase = firstText(request.username(), "svc-" + displayName);
        String username = uniqueUsername(usernameBase);
        User user = new User();
        user.setEmail(username + "-" + UUID.randomUUID() + "@service.trasck.local");
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setAccountType("service");
        user.setEmailVerified(true);
        user.setActive(true);
        return userRepository.save(user);
    }

    private void createWorkspaceMembership(UUID workspaceId, UUID userId, UUID roleId) {
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(workspaceId);
        membership.setUserId(userId);
        membership.setRoleId(roleId);
        membership.setStatus("active");
        membership.setJoinedAt(OffsetDateTime.now());
        workspaceMembershipRepository.save(membership);
    }

    private Role resolveWorkspaceRole(UUID workspaceId, UUID roleId) {
        if (roleId != null) {
            return roleRepository.findByIdAndWorkspaceIdAndProjectIdIsNull(roleId, workspaceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace role not found"));
        }
        return roleRepository.findByWorkspaceIdAndKeyIgnoreCaseAndProjectIdIsNull(workspaceId, "member")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Default workspace member role not found"));
    }

    private User activeUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!Boolean.TRUE.equals(user.getActive()) || user.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private void recordTokenEvent(UUID workspaceId, ApiToken token, String eventType) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("tokenId", token.getId().toString())
                .put("tokenType", token.getTokenType())
                .put("name", token.getName())
                .put("userId", token.getUserId().toString());
        if (token.getRoleId() != null) {
            payload.put("roleId", token.getRoleId().toString());
        }
        domainEventService.record(workspaceId, "api_token", token.getId(), eventType, payload);
    }

    private JsonNode scopes(JsonNode scopes) {
        return scopes == null || scopes.isNull() ? objectMapper.createArrayNode() : scopes;
    }

    private RawApiToken newRawToken(String typePrefix) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String value = typePrefix + HexFormat.of().formatHex(bytes);
        return new RawApiToken(value, value.substring(0, Math.min(18, value.length())));
    }

    private String uniqueUsername(String base) {
        String normalizedBase = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (normalizedBase.isBlank()) {
            normalizedBase = "service";
        }
        String candidate = normalizedBase;
        int suffix = 1;
        while (userRepository.existsByUsernameIgnoreCase(candidate)) {
            candidate = normalizedBase + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private String firstText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.trim();
    }

    private record RawApiToken(String value, String prefix) {
    }
}
