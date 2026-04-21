package com.strangequark.trasck.access;

import com.strangequark.trasck.identity.User;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SystemAdminResponse(
        UUID id,
        UUID userId,
        String email,
        String username,
        String displayName,
        Boolean active,
        UUID grantedById,
        OffsetDateTime grantedAt,
        OffsetDateTime revokedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static SystemAdminResponse from(SystemAdmin admin, User user) {
        return new SystemAdminResponse(
                admin.getId(),
                admin.getUserId(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getDisplayName(),
                admin.getActive(),
                admin.getGrantedById(),
                admin.getGrantedAt(),
                admin.getRevokedAt(),
                admin.getCreatedAt(),
                admin.getUpdatedAt()
        );
    }
}
