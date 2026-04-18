package com.strangequark.trasck.activity.storage;

public record StoredAttachment(
        String storageKey,
        long sizeBytes,
        String checksum
) {
}
