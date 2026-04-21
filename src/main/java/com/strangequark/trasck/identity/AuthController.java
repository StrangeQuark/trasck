package com.strangequark.trasck.identity;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final ApiTokenService apiTokenService;
    private final CurrentUserService currentUserService;
    private final boolean cookieSecure;
    private final boolean trustForwardedFor;

    public AuthController(
            AuthService authService,
            ApiTokenService apiTokenService,
            CurrentUserService currentUserService,
            @Value("${trasck.security.cookie-secure:false}") boolean cookieSecure,
            @Value("${trasck.security.trust-forwarded-for:false}") boolean trustForwardedFor
    ) {
        this.authService = authService;
        this.apiTokenService = apiTokenService;
        this.currentUserService = currentUserService;
        this.cookieSecure = cookieSecure;
        this.trustForwardedFor = trustForwardedFor;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authenticated(authService.login(request, remoteAddress(httpRequest)), HttpStatus.OK);
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return authenticated(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/auth/oauth/login")
    public ResponseEntity<AuthResponse> oauthLogin(@RequestBody OAuthLoginRequest request) {
        return authenticated(authService.oauthLogin(request), HttpStatus.OK);
    }

    @GetMapping("/auth/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    @GetMapping("/auth/me")
    public AuthUserResponse me() {
        return authService.currentUser(currentUserService.requireUserId());
    }

    @PostMapping("/auth/tokens/personal")
    public ResponseEntity<ApiTokenResponse> createPersonalToken(@RequestBody CreatePersonalTokenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiTokenService.createPersonalToken(currentUserService.requireUserId(), request));
    }

    @GetMapping("/auth/tokens/personal")
    public List<ApiTokenResponse> listPersonalTokens() {
        return apiTokenService.listPersonalTokens(currentUserService.requireUserId());
    }

    @DeleteMapping("/auth/tokens/{tokenId}")
    public ResponseEntity<Void> revokePersonalToken(@PathVariable UUID tokenId) {
        apiTokenService.revokePersonalToken(tokenId, currentUserService.requireUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workspaces/{workspaceId}/invitations")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public ResponseEntity<InvitationResponse> invite(
            @PathVariable UUID workspaceId,
            @RequestBody InviteUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.inviteUser(workspaceId, currentUserService.requireUserId(), request));
    }

    @PostMapping("/workspaces/{workspaceId}/users")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public ResponseEntity<AuthUserResponse> createUser(
            @PathVariable UUID workspaceId,
            @RequestBody AdminCreateUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.createUserInWorkspace(workspaceId, currentUserService.requireUserId(), request));
    }

    @PostMapping("/workspaces/{workspaceId}/service-tokens")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public ResponseEntity<ApiTokenResponse> createServiceToken(
            @PathVariable UUID workspaceId,
            @RequestBody CreateServiceTokenRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiTokenService.createServiceToken(workspaceId, currentUserService.requireUserId(), request));
    }

    @GetMapping("/workspaces/{workspaceId}/service-tokens")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public List<ApiTokenResponse> listServiceTokens(@PathVariable UUID workspaceId) {
        return apiTokenService.listServiceTokens(workspaceId);
    }

    @DeleteMapping("/workspaces/{workspaceId}/service-tokens/{tokenId}")
    @PreAuthorize("@permissionService.canManageUsers(authentication, #workspaceId)")
    public ResponseEntity<Void> revokeServiceToken(
            @PathVariable UUID workspaceId,
            @PathVariable UUID tokenId
    ) {
        apiTokenService.revokeServiceToken(workspaceId, tokenId, currentUserService.requireUserId());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> authenticated(AuthResponse response, HttpStatus status) {
        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.ACCESS_TOKEN_COOKIE, response.accessToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.between(OffsetDateTime.now(), response.expiresAt()))
                .build();
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    private String remoteAddress(HttpServletRequest request) {
        String forwardedFor = trustForwardedFor ? request.getHeader("X-Forwarded-For") : null;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
