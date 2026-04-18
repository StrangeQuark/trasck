package com.strangequark.trasck.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.access.Role;
import com.strangequark.trasck.access.RoleRepository;
import com.strangequark.trasck.access.ProjectMembership;
import com.strangequark.trasck.access.ProjectMembershipRepository;
import com.strangequark.trasck.access.WorkspaceMembership;
import com.strangequark.trasck.access.WorkspaceMembershipRepository;
import com.strangequark.trasck.event.DomainEventService;
import com.strangequark.trasck.project.Project;
import com.strangequark.trasck.project.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Set<String> SUPPORTED_OAUTH_PROVIDERS = Set.of("github", "google", "gitlab", "microsoft");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserAuthIdentityRepository userAuthIdentityRepository;
    private final UserInvitationRepository userInvitationRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final ProjectMembershipRepository projectMembershipRepository;
    private final RoleRepository roleRepository;
    private final ProjectRepository projectRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final DomainEventService domainEventService;
    private final ObjectMapper objectMapper;
    private final String oauthAssertionSecret;

    public AuthService(
            UserRepository userRepository,
            UserAuthIdentityRepository userAuthIdentityRepository,
            UserInvitationRepository userInvitationRepository,
            WorkspaceMembershipRepository workspaceMembershipRepository,
            ProjectMembershipRepository projectMembershipRepository,
            RoleRepository roleRepository,
            ProjectRepository projectRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            DomainEventService domainEventService,
            ObjectMapper objectMapper,
            @Value("${trasck.security.oauth-assertion-secret:}") String oauthAssertionSecret
    ) {
        this.userRepository = userRepository;
        this.userAuthIdentityRepository = userAuthIdentityRepository;
        this.userInvitationRepository = userInvitationRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.projectMembershipRepository = projectMembershipRepository;
        this.roleRepository = roleRepository;
        this.projectRepository = projectRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.domainEventService = domainEventService;
        this.objectMapper = objectMapper;
        this.oauthAssertionSecret = oauthAssertionSecret;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        LoginRequest login = required(request, "request");
        String identifier = requiredText(login.identifier(), "identifier");
        String password = requiredText(login.password(), "password");
        User user = userRepository.findByEmailIgnoreCase(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .orElseThrow(() -> unauthorized("Invalid username or password"));
        if (!isActive(user) || user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw unauthorized("Invalid username or password");
        }
        return issueAuth(user, "auth.login", null);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentUser(UUID userId) {
        return AuthUserResponse.from(activeUser(userId));
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        RegisterRequest registration = required(request, "request");
        String invitationToken = requiredText(registration.invitationToken(), "invitationToken");
        UserInvitation invitation = userInvitationRepository.findByTokenHash(hash(invitationToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Invitation is invalid"));
        OffsetDateTime now = OffsetDateTime.now();
        if (!"pending".equals(invitation.getStatus()) || !invitation.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invitation is no longer active");
        }

        String email = requiredText(registration.email(), "email").toLowerCase(Locale.ROOT);
        if (!email.equalsIgnoreCase(invitation.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration email must match the invitation");
        }
        User user = createHumanUser(
                email,
                requiredText(registration.username(), "username"),
                requiredText(registration.displayName(), "displayName"),
                requiredText(registration.password(), "password"),
                false
        );
        createWorkspaceMembership(invitation.getWorkspaceId(), user.getId(), invitation.getRoleId());
        if (invitation.getProjectId() != null) {
            createProjectMembership(invitation.getProjectId(), user.getId(), invitation.getProjectRoleId());
        }
        invitation.setStatus("accepted");
        invitation.setAcceptedById(user.getId());
        invitation.setAcceptedAt(now);
        userInvitationRepository.save(invitation);
        recordUserEvent(invitation.getWorkspaceId(), user, "auth.user_registered");
        return issueAuth(user, "auth.login", invitation.getWorkspaceId());
    }

    @Transactional
    public InvitationResponse inviteUser(UUID workspaceId, UUID invitedById, InviteUserRequest request) {
        InviteUserRequest invitationRequest = required(request, "request");
        String email = requiredText(invitationRequest.email(), "email").toLowerCase(Locale.ROOT);
        Role role = resolveWorkspaceRole(workspaceId, invitationRequest.roleId());
        ProjectInviteTarget projectInviteTarget = resolveProjectInviteTarget(workspaceId, invitationRequest.projectId(), invitationRequest.projectRoleId());
        String token = newToken();
        UserInvitation invitation = new UserInvitation();
        invitation.setWorkspaceId(workspaceId);
        invitation.setProjectId(projectInviteTarget.projectId());
        invitation.setEmail(email);
        invitation.setRoleId(role.getId());
        invitation.setProjectRoleId(projectInviteTarget.projectRoleId());
        invitation.setTokenHash(hash(token));
        invitation.setStatus("pending");
        invitation.setInvitedById(invitedById);
        invitation.setExpiresAt(invitationRequest.expiresAt() == null ? OffsetDateTime.now().plusDays(7) : invitationRequest.expiresAt());
        UserInvitation saved = userInvitationRepository.save(invitation);
        recordInvitationEvent(saved, "auth.user_invited");
        return InvitationResponse.from(saved, token);
    }

    @Transactional
    public AuthUserResponse createUserInWorkspace(UUID workspaceId, UUID createdById, AdminCreateUserRequest request) {
        AdminCreateUserRequest createRequest = required(request, "request");
        User user = createHumanUser(
                requiredText(createRequest.email(), "email").toLowerCase(Locale.ROOT),
                requiredText(createRequest.username(), "username"),
                requiredText(createRequest.displayName(), "displayName"),
                requiredText(createRequest.password(), "password"),
                Boolean.TRUE.equals(createRequest.emailVerified())
        );
        Role role = resolveWorkspaceRole(workspaceId, createRequest.roleId());
        createWorkspaceMembership(workspaceId, user.getId(), role.getId());
        recordUserEvent(workspaceId, user, "auth.user_created");
        ObjectNode auditPayload = userPayload(user)
                .put("createdById", createdById.toString())
                .put("roleId", role.getId().toString());
        domainEventService.record(workspaceId, "user", user.getId(), "auth.user_added_to_workspace", auditPayload);
        return AuthUserResponse.from(user);
    }

    @Transactional
    public AuthResponse oauthLogin(OAuthLoginRequest request) {
        OAuthLoginRequest oauth = required(request, "request");
        String provider = requiredText(oauth.provider(), "provider").toLowerCase(Locale.ROOT);
        if (!SUPPORTED_OAUTH_PROVIDERS.contains(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported OAuth provider");
        }
        String subject = requiredText(oauth.providerSubject(), "providerSubject");
        verifyOAuthAssertion(provider, subject, oauth);
        return oauthLoginFromVerifiedProfile(new OAuthProviderProfile(
                provider,
                subject,
                oauth.providerEmail(),
                oauth.emailVerified(),
                oauth.providerUsername(),
                oauth.displayName(),
                oauth.avatarUrl(),
                toJsonNode(oauth.metadata())
        ));
    }

    @Transactional
    public AuthResponse oauthLoginFromProvider(OAuthProviderProfile profile) {
        OAuthProviderProfile verifiedProfile = required(profile, "profile");
        String provider = requiredText(verifiedProfile.provider(), "provider").toLowerCase(Locale.ROOT);
        if (!SUPPORTED_OAUTH_PROVIDERS.contains(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported OAuth provider");
        }
        return oauthLoginFromVerifiedProfile(new OAuthProviderProfile(
                provider,
                requiredText(verifiedProfile.providerSubject(), "providerSubject"),
                verifiedProfile.providerEmail(),
                verifiedProfile.emailVerified(),
                verifiedProfile.providerUsername(),
                verifiedProfile.displayName(),
                verifiedProfile.avatarUrl(),
                verifiedProfile.metadata()
        ));
    }

    private AuthResponse oauthLoginFromVerifiedProfile(OAuthProviderProfile profile) {
        String provider = profile.provider();
        String subject = profile.providerSubject();
        User user = userAuthIdentityRepository.findByProviderAndProviderSubject(provider, subject)
                .map(identity -> activeUser(identity.getUserId()))
                .orElseGet(() -> linkNewOAuthIdentity(profile));
        return issueAuth(user, "auth.oauth_login", null);
    }

    private User linkNewOAuthIdentity(OAuthProviderProfile profile) {
        if (!Boolean.TRUE.equals(profile.emailVerified())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A verified provider email is required for automatic OAuth linking");
        }
        String providerEmail = requiredText(profile.providerEmail(), "providerEmail").toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(providerEmail)
                .orElseGet(() -> createOAuthUser(providerEmail, profile));

        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUserId(user.getId());
        identity.setProvider(profile.provider());
        identity.setProviderSubject(profile.providerSubject());
        identity.setProviderUsername(profile.providerUsername());
        identity.setProviderEmail(providerEmail);
        identity.setMetadata(metadataWithVerifiedEmail(profile.metadata(), profile.emailVerified()));
        userAuthIdentityRepository.save(identity);
        recordUserEvent(null, user, "auth.oauth_identity_linked");
        return user;
    }

    private User createHumanUser(String email, String username, String displayName, String password, boolean emailVerified) {
        assertUniqueUser(email, username);
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setAccountType("human");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(emailVerified);
        user.setActive(true);
        return userRepository.save(user);
    }

    private User createOAuthUser(String email, OAuthProviderProfile profile) {
        String username = uniqueUsername(firstText(profile.providerUsername(), email.substring(0, email.indexOf('@'))));
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setDisplayName(firstText(profile.displayName(), username));
        user.setAvatarUrl(profile.avatarUrl());
        user.setAccountType("human");
        user.setEmailVerified(true);
        user.setActive(true);
        return userRepository.save(user);
    }

    private void assertUniqueUser(String email, String username) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this username already exists");
        }
    }

    private AuthResponse issueAuth(User user, String eventType, UUID workspaceId) {
        user.setLastLoginAt(OffsetDateTime.now());
        User saved = userRepository.save(user);
        JwtTokenService.AuthToken token = jwtTokenService.issue(saved);
        recordUserEvent(workspaceId, saved, eventType);
        return new AuthResponse(AuthUserResponse.from(saved), "Bearer", token.value(), token.expiresAt());
    }

    private void createWorkspaceMembership(UUID workspaceId, UUID userId, UUID roleId) {
        if (workspaceMembershipRepository.existsByWorkspaceIdAndUserIdAndStatusIgnoreCase(workspaceId, userId, "active")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already an active workspace member");
        }
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspaceId(workspaceId);
        membership.setUserId(userId);
        membership.setRoleId(roleId);
        membership.setStatus("active");
        membership.setJoinedAt(OffsetDateTime.now());
        workspaceMembershipRepository.save(membership);
    }

    private void createProjectMembership(UUID projectId, UUID userId, UUID roleId) {
        if (projectMembershipRepository.existsByProjectIdAndUserIdAndStatusIgnoreCase(projectId, userId, "active")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already an active project member");
        }
        ProjectMembership membership = new ProjectMembership();
        membership.setProjectId(projectId);
        membership.setUserId(userId);
        membership.setRoleId(roleId);
        membership.setStatus("active");
        projectMembershipRepository.save(membership);
    }

    private Role resolveWorkspaceRole(UUID workspaceId, UUID roleId) {
        if (roleId != null) {
            return roleRepository.findByIdAndWorkspaceIdAndProjectIdIsNull(roleId, workspaceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace role not found"));
        }
        return roleRepository.findByWorkspaceIdAndKeyIgnoreCaseAndProjectIdIsNull(workspaceId, "member")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Default workspace member role not found"));
    }

    private ProjectInviteTarget resolveProjectInviteTarget(UUID workspaceId, UUID projectId, UUID projectRoleId) {
        if (projectId == null && projectRoleId == null) {
            return new ProjectInviteTarget(null, null);
        }
        if (projectId == null || projectRoleId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId and projectRoleId must be provided together");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project not found"));
        if (!workspaceId.equals(project.getWorkspaceId()) || project.getDeletedAt() != null || !"active".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project not found in workspace");
        }
        Role projectRole = roleRepository.findByIdAndProjectId(projectRoleId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project role not found"));
        return new ProjectInviteTarget(project.getId(), projectRole.getId());
    }

    private User activeUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!isActive(user)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private boolean isActive(User user) {
        return Boolean.TRUE.equals(user.getActive()) && user.getDeletedAt() == null;
    }

    private JsonNode metadataWithVerifiedEmail(JsonNode metadata, Boolean emailVerified) {
        ObjectNode result = objectMapper.createObjectNode();
        if (metadata != null && !metadata.isNull()) {
            result.set("providerMetadata", metadata);
        }
        result.put("emailVerified", Boolean.TRUE.equals(emailVerified));
        return result;
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private void recordUserEvent(UUID workspaceId, User user, String eventType) {
        domainEventService.record(workspaceId, "user", user.getId(), eventType, userPayload(user));
    }

    private void recordInvitationEvent(UserInvitation invitation, String eventType) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("workspaceId", invitation.getWorkspaceId().toString())
                .put("email", invitation.getEmail())
                .put("status", invitation.getStatus());
        if (invitation.getInvitedById() != null) {
            payload.put("invitedById", invitation.getInvitedById().toString());
        }
        if (invitation.getRoleId() != null) {
            payload.put("roleId", invitation.getRoleId().toString());
        }
        if (invitation.getProjectId() != null) {
            payload.put("projectId", invitation.getProjectId().toString());
        }
        if (invitation.getProjectRoleId() != null) {
            payload.put("projectRoleId", invitation.getProjectRoleId().toString());
        }
        domainEventService.record(invitation.getWorkspaceId(), "user_invitation", invitation.getId(), eventType, payload);
    }

    private ObjectNode userPayload(User user) {
        return objectMapper.createObjectNode()
                .put("userId", user.getId().toString())
                .put("email", user.getEmail())
                .put("username", user.getUsername())
                .put("accountType", user.getAccountType());
    }

    private String uniqueUsername(String base) {
        String normalizedBase = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (normalizedBase.isBlank()) {
            normalizedBase = "user";
        }
        String candidate = normalizedBase;
        int suffix = 1;
        while (userRepository.existsByUsernameIgnoreCase(candidate)) {
            candidate = normalizedBase + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private void verifyOAuthAssertion(String provider, String subject, OAuthLoginRequest request) {
        if (oauthAssertionSecret == null || oauthAssertionSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OAuth assertion verification is not configured");
        }
        String expected = hmacSha256(oauthAssertionPayload(provider, subject, request), oauthAssertionSecret);
        String provided = requiredText(request.assertion(), "assertion");
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OAuth assertion is invalid");
        }
    }

    private String oauthAssertionPayload(String provider, String subject, OAuthLoginRequest request) {
        return provider + "\n"
                + subject + "\n"
                + requiredText(request.providerEmail(), "providerEmail").toLowerCase(Locale.ROOT) + "\n"
                + Boolean.TRUE.equals(request.emailVerified());
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 is required", ex);
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
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

    private record ProjectInviteTarget(UUID projectId, UUID projectRoleId) {
    }
}
