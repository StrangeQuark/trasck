package com.strangequark.trasck.activity.storage;

import com.strangequark.trasck.activity.AttachmentStorageConfig;
import java.util.Set;

public interface AttachmentStorageProvider {
    Set<String> providerKeys();

    void store(AttachmentStorageConfig config, AttachmentStorageWrite write);

    byte[] read(AttachmentStorageConfig config, String storageKey);

    void delete(AttachmentStorageConfig config, String storageKey);
}
