package com.strangequark.trasck.security;

public record ContentLimits(
        long attachmentMaxUploadBytes,
        long attachmentMaxDownloadBytes,
        String attachmentAllowedContentTypes,
        long exportMaxArtifactBytes,
        String exportAllowedContentTypes,
        long importMaxParseBytes,
        String importAllowedContentTypes
) {
}
