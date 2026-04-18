package com.strangequark.trasck.identity;

import java.util.Collection;
import java.util.List;
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

    public TrasckPrincipal(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash() == null ? "" : user.getPasswordHash();
        this.enabled = Boolean.TRUE.equals(user.getActive()) && user.getDeletedAt() == null;
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
