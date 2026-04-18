package com.strangequark.trasck.identity;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final CurrentUserService currentUserService;
    private final boolean cookieSecure;

    public AuthController(
            AuthService authService,
            CurrentUserService currentUserService,
            @Value("${trasck.security.cookie-secure:false}") boolean cookieSecure
    ) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return authenticated(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return authenticated(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/auth/oauth/login")
    public ResponseEntity<AuthResponse> oauthLogin(@RequestBody OAuthLoginRequest request) {
        return authenticated(authService.oauthLogin(request), HttpStatus.OK);
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
}
