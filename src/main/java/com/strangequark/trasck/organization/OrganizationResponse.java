package com.strangequark.trasck.organization;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        String status,
        UUID createdById,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getStatus(),
                organization.getCreatedById(),
                organization.getCreatedAt(),
                organization.getUpdatedAt()
        );
    }
}
