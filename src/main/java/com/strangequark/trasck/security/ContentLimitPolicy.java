package com.strangequark.trasck.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ContentLimitPolicy {

    private final WorkspaceSecurityPolicyService policyService;

    public ContentLimitPolicy(WorkspaceSecurityPolicyService policyService) {
        this.policyService = policyService;
    }

    public void validateAttachmentMetadata(UUID workspaceId, String filename, String contentType, long sizeBytes) {
        validateAttachmentMetadata(workspaceId, null, filename, contentType, sizeBytes);
    }

    public void validateAttachmentMetadata(UUID workspaceId, UUID projectId, String filename, String contentType, long sizeBytes) {
        ContentLimits limits = policyService.limits(workspaceId, projectId);
        validateSize("attachment sizeBytes", sizeBytes, limits.attachmentMaxUploadBytes());
        validateContentType("attachment contentType", csvSet(limits.attachmentAllowedContentTypes()), fallbackContentType(filename, contentType));
    }

    public void validateAttachmentUpload(UUID workspaceId, String filename, String contentType, byte[] content) {
        validateAttachmentUpload(workspaceId, null, filename, contentType, content);
    }

    public void validateAttachmentUpload(UUID workspaceId, UUID projectId, String filename, String contentType, byte[] content) {
        ContentLimits limits = policyService.limits(workspaceId, projectId);
        validateSize("attachment file", content == null ? 0 : content.length, limits.attachmentMaxUploadBytes());
        validateContentType("attachment contentType", csvSet(limits.attachmentAllowedContentTypes()), fallbackContentType(filename, contentType));
    }

    public void validateAttachmentDownload(UUID workspaceId, String filename, String contentType, Long sizeBytes) {
        validateAttachmentDownload(workspaceId, null, filename, contentType, sizeBytes);
    }

    public void validateAttachmentDownload(UUID workspaceId, UUID projectId, String filename, String contentType, Long sizeBytes) {
        ContentLimits limits = policyService.limits(workspaceId, projectId);
        if (sizeBytes != null) {
            validateSize("attachment download", sizeBytes, limits.attachmentMaxDownloadBytes());
        }
        validateContentType("attachment contentType", csvSet(limits.attachmentAllowedContentTypes()), fallbackContentType(filename, contentType));
    }

    public void validateGeneratedExport(UUID workspaceId, String filename, String contentType, byte[] content) {
        validateGeneratedExport(workspaceId, null, filename, contentType, content);
    }

    public void validateGeneratedExport(UUID workspaceId, UUID projectId, String filename, String contentType, byte[] content) {
        ContentLimits limits = policyService.limits(workspaceId, projectId);
        validateSize("export artifact", content == null ? 0 : content.length, limits.exportMaxArtifactBytes());
        validateContentType("export contentType", csvSet(limits.exportAllowedContentTypes()), fallbackContentType(filename, contentType));
    }

    public void validateExportDownload(UUID workspaceId, String filename, String contentType, Long sizeBytes) {
        validateExportDownload(workspaceId, null, filename, contentType, sizeBytes);
    }

    public void validateExportDownload(UUID workspaceId, UUID projectId, String filename, String contentType, Long sizeBytes) {
        ContentLimits limits = policyService.limits(workspaceId, projectId);
        if (sizeBytes != null) {
            validateSize("export download", sizeBytes, limits.exportMaxArtifactBytes());
        }
        validateContentType("export contentType", csvSet(limits.exportAllowedContentTypes()), fallbackContentType(filename, contentType));
    }

    public void validateImportParse(UUID workspaceId, String provider, String sourceType, String contentType, String content) {
        validateImportParse(workspaceId, null, provider, sourceType, contentType, content);
    }

    public void validateImportParse(UUID workspaceId, UUID projectId, String provider, String sourceType, String contentType, String content) {
        ContentLimits limits = policyService.limits(workspaceId, projectId);
        validateSize("import content", content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length, limits.importMaxParseBytes());
        validateContentType("import contentType", csvSet(limits.importAllowedContentTypes()), fallbackImportContentType(provider, sourceType, contentType));
    }

    private Set<String> csvSet(String value) {
        return Arrays.stream((value == null ? "" : value).split(","))
                .map(this::normalizeContentType)
                .filter(type -> !type.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private void validateSize(String fieldName, long sizeBytes, long maxBytes) {
        if (sizeBytes < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than or equal to 0");
        }
        if (sizeBytes > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, fieldName + " exceeds the configured limit");
        }
    }

    private void validateContentType(String fieldName, Set<String> allowed, String effectiveContentType) {
        if (!allowed.contains(normalizeContentType(effectiveContentType))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, fieldName + " is not allowed");
        }
    }

    private String fallbackImportContentType(String provider, String sourceType, String contentType) {
        if (hasText(contentType)) {
            return contentType;
        }
        String source = firstText(sourceType, provider).toLowerCase(Locale.ROOT);
        if ("csv".equals(source) || source.contains("csv") || "row".equals(source)) {
            return "text/csv";
        }
        if ("jira".equals(source) || "rally".equals(source) || source.contains("json")) {
            return "application/json";
        }
        return "text/plain";
    }

    private String fallbackContentType(String filename, String contentType) {
        if (hasText(contentType)) {
            return contentType;
        }
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String value = separator < 0 ? contentType : contentType.substring(0, separator);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstText(String preferred, String fallback) {
        return hasText(preferred) ? preferred.trim() : (fallback == null ? "" : fallback.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
