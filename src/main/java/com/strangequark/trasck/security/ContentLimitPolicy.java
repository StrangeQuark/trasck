package com.strangequark.trasck.security;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ContentLimitPolicy {

    private final long maxAttachmentUploadBytes;
    private final long maxAttachmentDownloadBytes;
    private final long maxExportArtifactBytes;
    private final long maxImportParseBytes;
    private final Set<String> attachmentContentTypes;
    private final Set<String> exportContentTypes;
    private final Set<String> importContentTypes;

    public ContentLimitPolicy(
            @Value("${trasck.attachments.max-upload-bytes:10485760}") long maxAttachmentUploadBytes,
            @Value("${trasck.attachments.max-download-bytes:52428800}") long maxAttachmentDownloadBytes,
            @Value("${trasck.attachments.allowed-content-types:text/plain,text/markdown,text/csv,application/json,application/pdf,image/png,image/jpeg,image/gif,image/webp,application/zip,application/octet-stream}") String attachmentContentTypes,
            @Value("${trasck.exports.max-artifact-bytes:52428800}") long maxExportArtifactBytes,
            @Value("${trasck.exports.allowed-content-types:application/json,text/csv,application/octet-stream}") String exportContentTypes,
            @Value("${trasck.imports.max-parse-bytes:5242880}") long maxImportParseBytes,
            @Value("${trasck.imports.allowed-content-types:text/csv,application/csv,application/json,text/json,text/plain}") String importContentTypes
    ) {
        this.maxAttachmentUploadBytes = positive(maxAttachmentUploadBytes);
        this.maxAttachmentDownloadBytes = positive(maxAttachmentDownloadBytes);
        this.maxExportArtifactBytes = positive(maxExportArtifactBytes);
        this.maxImportParseBytes = positive(maxImportParseBytes);
        this.attachmentContentTypes = csvSet(attachmentContentTypes);
        this.exportContentTypes = csvSet(exportContentTypes);
        this.importContentTypes = csvSet(importContentTypes);
    }

    public void validateAttachmentMetadata(String filename, String contentType, long sizeBytes) {
        validateSize("attachment sizeBytes", sizeBytes, maxAttachmentUploadBytes);
        validateContentType("attachment contentType", contentType, attachmentContentTypes, fallbackContentType(filename, contentType));
    }

    public void validateAttachmentUpload(String filename, String contentType, byte[] content) {
        validateSize("attachment file", content == null ? 0 : content.length, maxAttachmentUploadBytes);
        validateContentType("attachment contentType", contentType, attachmentContentTypes, fallbackContentType(filename, contentType));
    }

    public void validateAttachmentDownload(String filename, String contentType, Long sizeBytes) {
        if (sizeBytes != null) {
            validateSize("attachment download", sizeBytes, maxAttachmentDownloadBytes);
        }
        validateContentType("attachment contentType", contentType, attachmentContentTypes, fallbackContentType(filename, contentType));
    }

    public void validateGeneratedExport(String filename, String contentType, byte[] content) {
        validateSize("export artifact", content == null ? 0 : content.length, maxExportArtifactBytes);
        validateContentType("export contentType", contentType, exportContentTypes, fallbackContentType(filename, contentType));
    }

    public void validateExportDownload(String filename, String contentType, Long sizeBytes) {
        if (sizeBytes != null) {
            validateSize("export download", sizeBytes, maxExportArtifactBytes);
        }
        validateContentType("export contentType", contentType, exportContentTypes, fallbackContentType(filename, contentType));
    }

    public void validateImportParse(String provider, String sourceType, String contentType, String content) {
        validateSize("import content", content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length, maxImportParseBytes);
        validateContentType("import contentType", contentType, importContentTypes, fallbackImportContentType(provider, sourceType, contentType));
    }

    private long positive(long value) {
        return Math.max(1, value);
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

    private void validateContentType(String fieldName, String originalContentType, Set<String> allowed, String effectiveContentType) {
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
