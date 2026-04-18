package com.strangequark.trasck.identity;

import java.util.Collection;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class TrasckPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final UUID apiTokenId;
    private final String apiTokenType;
    private final Set<String> apiTokenScopes;

    public TrasckPrincipal(User user) {
        this(user, null, null, Set.of());
    }

    public TrasckPrincipal(User user, UUID apiTokenId, String apiTokenType, Set<String> apiTokenScopes) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash() == null ? "" : user.getPasswordHash();
        this.enabled = Boolean.TRUE.equals(user.getActive()) && user.getDeletedAt() == null;
        this.apiTokenId = apiTokenId;
        this.apiTokenType = apiTokenType;
        this.apiTokenScopes = apiTokenScopes == null ? Set.of() : Set.copyOf(apiTokenScopes);
    }

    public UUID userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public UUID apiTokenId() {
        return apiTokenId;
    }

    public String apiTokenType() {
        return apiTokenType;
    }

    public boolean isApiTokenAuthenticated() {
        return apiTokenId != null;
    }

    public boolean allowsScope(String permissionKey) {
        if (!isApiTokenAuthenticated()) {
            return true;
        }
        if (apiTokenScopes.contains("*") || apiTokenScopes.contains(permissionKey)) {
            return true;
        }
        int separator = permissionKey.lastIndexOf('.');
        if (separator > 0) {
            return apiTokenScopes.contains(permissionKey.substring(0, separator).toLowerCase(Locale.ROOT) + ".*");
        }
        return false;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
