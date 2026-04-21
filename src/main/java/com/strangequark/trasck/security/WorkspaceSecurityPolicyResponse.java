package com.strangequark.trasck.security;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkspaceSecurityPolicyResponse(
        UUID workspaceId,
        Boolean anonymousReadEnabled,
        Long attachmentMaxUploadBytes,
        Long attachmentMaxDownloadBytes,
        String attachmentAllowedContentTypes,
        Long exportMaxArtifactBytes,
        String exportAllowedContentTypes,
        Long importMaxParseBytes,
        String importAllowedContentTypes,
        Boolean customPolicy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
