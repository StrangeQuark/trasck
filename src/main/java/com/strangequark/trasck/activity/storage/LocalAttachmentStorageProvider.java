package com.strangequark.trasck.activity.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LocalAttachmentStorageProvider implements AttachmentStorageProvider {

    private final String defaultRootPath;

    public LocalAttachmentStorageProvider(@Value("${trasck.attachments.local-root:}") String defaultRootPath) {
        this.defaultRootPath = defaultRootPath;
    }

    @Override
    public Set<String> providerKeys() {
        return Set.of("filesystem", "local");
    }

    @Override
    public void store(AttachmentStorageConfig config, AttachmentStorageWrite write) {
        Path target = resolve(config, write.storageKey());
        try {
            if (createDirectories(config)) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, write.content(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to write attachment file", ex);
        }
    }

    @Override
    public byte[] read(AttachmentStorageConfig config, String storageKey) {
        try {
            return Files.readAllBytes(resolve(config, storageKey));
        } catch (NoSuchFileException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file not found", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to read attachment file", ex);
        }
    }

    @Override
    public void delete(AttachmentStorageConfig config, String storageKey) {
        try {
            Files.deleteIfExists(resolve(config, storageKey));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to delete attachment file", ex);
        }
    }

    private Path resolve(AttachmentStorageConfig config, String storageKey) {
        try {
            Path root = root(config);
            Path target = root.resolve(storageKey).normalize();
            if (!target.startsWith(root)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storageKey must stay within the configured attachment root");
            }
            return target;
        } catch (InvalidPathException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment storage path", ex);
        }
    }

    private Path root(AttachmentStorageConfig config) {
        String configuredRoot = text(config.getConfig(), "rootPath");
        if (configuredRoot == null) {
            configuredRoot = text(config.getConfig(), "basePath");
        }
        if (configuredRoot == null || configuredRoot.isBlank()) {
            configuredRoot = defaultRootPath == null || defaultRootPath.isBlank()
                    ? Paths.get(System.getProperty("java.io.tmpdir"), "trasck-attachments").toString()
                    : defaultRootPath;
        }
        return Paths.get(configuredRoot).toAbsolutePath().normalize();
    }

    private boolean createDirectories(AttachmentStorageConfig config) {
        JsonNode value = config.getConfig() == null ? null : config.getConfig().get("createDirectories");
        return value == null || value.asBoolean(true);
    }

    private String text(JsonNode config, String key) {
        if (config == null || config.isNull()) {
            return null;
        }
        JsonNode value = config.get(key);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }
}
