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
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptService loginAttemptService;
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
            PasswordPolicy passwordPolicy,
            LoginAttemptService loginAttemptService,
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
        this.passwordPolicy = passwordPolicy;
        this.loginAttemptService = loginAttemptService;
        this.jwtTokenService = jwtTokenService;
        this.domainEventService = domainEventService;
        this.objectMapper = objectMapper;
        this.oauthAssertionSecret = oauthAssertionSecret;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String remoteAddress) {
        LoginRequest login = required(request, "request");
        String identifier = requiredText(login.identifier(), "identifier");
        String password = requiredText(login.password(), "password");
        loginAttemptService.assertAllowed(identifier, remoteAddress);
        User user = userRepository.findByEmailIgnoreCase(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .orElse(null);
        if (user == null) {
            loginAttemptService.recordFailure(identifier, remoteAddress);
            throw unauthorized("Invalid username or password");
        }
        if (!isActive(user) || user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(identifier, remoteAddress);
            throw unauthorized("Invalid username or password");
        }
        loginAttemptService.recordSuccess(identifier, remoteAddress);
        return issueAuth(user, "auth.login", null);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentUser(UUID userId) {
        return AuthUserResponse.from(activeUser(userId));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInvitationResponse> listInvitations(UUID workspaceId, String status) {
        UUID workspace = required(workspaceId, "workspaceId");
        String normalizedStatus = normalizeStatus(status, "pending");
        List<UserInvitation> invitations = "all".equals(normalizedStatus)
                ? userInvitationRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspace)
                : userInvitationRepository.findByWorkspaceIdAndStatusIgnoreCaseOrderByCreatedAtDesc(workspace, normalizedStatus);
        Map<UUID, Role> roles = rolesById(invitations.stream()
                .flatMap(invitation -> Stream.of(invitation.getRoleId(), invitation.getProjectRoleId()))
                .filter(Objects::nonNull)
                .toList());
        return invitations.stream()
                .map(invitation -> WorkspaceInvitationResponse.from(
                        invitation,
                        roles.get(invitation.getRoleId()),
                        roles.get(invitation.getProjectRoleId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listWorkspaceUsers(UUID workspaceId, String status) {
        UUID workspace = required(workspaceId, "workspaceId");
        String normalizedStatus = normalizeStatus(status, "active");
        List<WorkspaceMembership> memberships = "all".equals(normalizedStatus)
                ? workspaceMembershipRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspace)
                : workspaceMembershipRepository.findByWorkspaceIdAndStatusIgnoreCaseOrderByJoinedAtDescCreatedAtDesc(workspace, normalizedStatus);
        Map<UUID, User> users = usersById(memberships.stream()
                .map(WorkspaceMembership::getUserId)
                .filter(Objects::nonNull)
                .toList());
        Map<UUID, Role> roles = rolesById(memberships.stream()
                .map(WorkspaceMembership::getRoleId)
                .filter(Objects::nonNull)
                .toList());
        return memberships.stream()
                .filter(membership -> isHumanUser(users.get(membership.getUserId())))
                .map(membership -> WorkspaceMemberResponse.from(membership, users.get(membership.getUserId()), roles.get(membership.getRoleId())))
                .toList();
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
    public void cancelInvitation(UUID workspaceId, UUID invitationId, UUID actorId) {
        UUID workspace = required(workspaceId, "workspaceId");
        UUID invitation = required(invitationId, "invitationId");
        UserInvitation userInvitation = userInvitationRepository.findByIdAndWorkspaceId(invitation, workspace)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        String status = userInvitation.getStatus() == null ? "" : userInvitation.getStatus().toLowerCase(Locale.ROOT);
        if ("accepted".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accepted invitations cannot be revoked");
        }
        if (!"revoked".equals(status)) {
            userInvitation.setStatus("revoked");
            userInvitationRepository.save(userInvitation);
            ObjectNode payload = invitationPayload(userInvitation).put("revokedById", required(actorId, "actorId").toString());
            domainEventService.record(workspace, "user_invitation", userInvitation.getId(), "auth.user_invitation_revoked", payload);
        }
    }

    @Transactional
    public void removeUserFromWorkspace(UUID workspaceId, UUID userId, UUID actorId) {
        UUID workspace = required(workspaceId, "workspaceId");
        UUID userToRemove = required(userId, "userId");
        UUID actor = required(actorId, "actorId");
        if (actor.equals(userToRemove)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Current user cannot remove themselves from the workspace");
        }

        User user = userRepository.findById(userToRemove)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        WorkspaceMembership membership = workspaceMembershipRepository
                .findByWorkspaceIdAndUserIdAndStatusIgnoreCase(workspace, userToRemove, "active")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace user not found"));
        membership.setStatus("removed");
        workspaceMembershipRepository.save(membership);

        projectMembershipRepository.findByWorkspaceIdAndUserIdAndStatusIgnoreCase(workspace, userToRemove, "active")
                .forEach(projectMembership -> {
                    projectMembership.setStatus("removed");
                    projectMembershipRepository.save(projectMembership);
                });

        if (workspaceMembershipRepository.findByUserIdAndStatusIgnoreCase(userToRemove, "active").isEmpty()) {
            user.setActive(false);
            user.setDeletedAt(OffsetDateTime.now());
            userRepository.save(user);
        }

        ObjectNode payload = userPayload(user)
                .put("workspaceId", workspace.toString())
                .put("removedById", actor.toString());
        domainEventService.record(workspace, "user", user.getId(), "auth.user_removed_from_workspace", payload);
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
        passwordPolicy.validateNewPassword(password, "password");
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
        WorkspaceMembership membership = workspaceMembershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseGet(WorkspaceMembership::new);
        if ("active".equalsIgnoreCase(membership.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already an active workspace member");
        }
        membership.setWorkspaceId(workspaceId);
        membership.setUserId(userId);
        membership.setRoleId(roleId);
        membership.setStatus("active");
        membership.setJoinedAt(OffsetDateTime.now());
        workspaceMembershipRepository.save(membership);
    }

    private void createProjectMembership(UUID projectId, UUID userId, UUID roleId) {
        ProjectMembership membership = projectMembershipRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseGet(ProjectMembership::new);
        if ("active".equalsIgnoreCase(membership.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already an active project member");
        }
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

    private boolean isHumanUser(User user) {
        return user != null && "human".equalsIgnoreCase(user.getAccountType());
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
        domainEventService.record(invitation.getWorkspaceId(), "user_invitation", invitation.getId(), eventType, invitationPayload(invitation));
    }

    private ObjectNode invitationPayload(UserInvitation invitation) {
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
        return payload;
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

    private String normalizeStatus(String status, String defaultStatus) {
        if (status == null || status.isBlank()) {
            return defaultStatus;
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }

    private Map<UUID, Role> rolesById(Collection<UUID> roleIds) {
        List<UUID> ids = distinctIds(roleIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return roleRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));
    }

    private Map<UUID, User> usersById(Collection<UUID> userIds) {
        List<UUID> ids = distinctIds(userIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private List<UUID> distinctIds(Collection<UUID> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private record ProjectInviteTarget(UUID projectId, UUID projectRoleId) {
    }
}
