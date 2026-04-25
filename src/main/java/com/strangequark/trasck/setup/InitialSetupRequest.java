package com.strangequark.trasck.setup;

public record InitialSetupRequest(
        AdminUserRequest adminUser
) {
    public record AdminUserRequest(
            String email,
            String username,
            String displayName,
            String password
    ) {
    }

    public record OrganizationRequest(
            String name,
            String slug
    ) {
    }

    public record WorkspaceRequest(
            String name,
            String key,
            String timezone,
            String locale,
            Boolean anonymousReadEnabled
    ) {
    }

    public record ProjectRequest(
            String name,
            String key,
            String description,
            String visibility
    ) {
    }
}
