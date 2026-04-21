package com.strangequark.trasck.security;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectSecurityPolicyResponse(
        UUID projectId,
        UUID workspaceId,
        String visibility,
        Boolean workspaceAnonymousReadEnabled,
        Boolean publicReadEnabled,
        Long attachmentMaxUploadBytes,
        Long attachmentMaxDownloadBytes,
        String attachmentAllowedContentTypes,
        Long exportMaxArtifactBytes,
        String exportAllowedContentTypes,
        Long importMaxParseBytes,
        String importAllowedContentTypes,
        Boolean workspaceCustomPolicy,
        Boolean customPolicy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
