package com.strangequark.trasck.security;

public record WorkspaceSecurityPolicyRequest(
        Long attachmentMaxUploadBytes,
        Long attachmentMaxDownloadBytes,
        String attachmentAllowedContentTypes,
        Long exportMaxArtifactBytes,
        String exportAllowedContentTypes,
        Long importMaxParseBytes,
        String importAllowedContentTypes
) {
}
