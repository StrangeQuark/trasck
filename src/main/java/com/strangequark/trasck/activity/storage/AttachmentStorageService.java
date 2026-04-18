package com.strangequark.trasck.activity.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttachmentStorageService {

    private static final DateTimeFormatter STORAGE_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Map<String, AttachmentStorageProvider> providers;

    public AttachmentStorageService(List<AttachmentStorageProvider> providers) {
        this.providers = new HashMap<>();
        for (AttachmentStorageProvider provider : providers) {
            for (String key : provider.providerKeys()) {
                this.providers.put(key.toLowerCase(Locale.ROOT), provider);
            }
        }
    }

    public StoredAttachment store(AttachmentStorageConfig config, AttachmentUpload upload) {
        AttachmentStorageConfig storageConfig = required(config, "storageConfig");
        AttachmentUpload attachmentUpload = required(upload, "upload");
        byte[] content = attachmentUpload.content() == null ? new byte[0] : attachmentUpload.content();
        String checksum = checksum(content);
        if (attachmentUpload.checksum() != null && !attachmentUpload.checksum().isBlank()
                && !checksum.equals(normalizeChecksum(attachmentUpload.checksum()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checksum does not match uploaded file");
        }
        String storageKey = storageKey(storageConfig, attachmentUpload.filename());
        provider(storageConfig).store(storageConfig, new AttachmentStorageWrite(storageKey, attachmentUpload.contentType(), content));
        return new StoredAttachment(storageKey, content.length, checksum);
    }

    public byte[] read(AttachmentStorageConfig config, String storageKey) {
        return provider(required(config, "storageConfig")).read(config, requiredText(storageKey, "storageKey"));
    }

    public void delete(AttachmentStorageConfig config, String storageKey) {
        provider(required(config, "storageConfig")).delete(config, requiredText(storageKey, "storageKey"));
    }

    private AttachmentStorageProvider provider(AttachmentStorageConfig config) {
        String providerKey = requiredText(config.getProvider(), "provider").toLowerCase(Locale.ROOT);
        AttachmentStorageProvider provider = providers.get(providerKey);
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported attachment storage provider: " + providerKey);
        }
        return provider;
    }

    private String storageKey(AttachmentStorageConfig config, String filename) {
        String prefix = firstText(config.getConfig(), "keyPrefix", "prefix");
        String normalizedPrefix = normalizePrefix(prefix);
        String datePath = LocalDate.now(ZoneOffset.UTC).format(STORAGE_DATE);
        return normalizedPrefix
                + config.getWorkspaceId()
                + "/"
                + datePath
                + "/"
                + UUID.randomUUID()
                + "-"
                + sanitizeFilename(filename);
    }

    private String sanitizeFilename(String filename) {
        String name = requiredText(filename, "filename").replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            name = "attachment";
        }
        return name.length() > 180 ? name.substring(name.length() - 180) : name;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String normalized = prefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "" : normalized + "/";
    }

    private String firstText(JsonNode config, String... keys) {
        if (config == null || config.isNull()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = config.get(key);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private String checksum(byte[] content) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private String normalizeChecksum(String checksum) {
        String normalized = checksum.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("sha256:") ? normalized : "sha256:" + normalized;
    }

    private <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }
}
