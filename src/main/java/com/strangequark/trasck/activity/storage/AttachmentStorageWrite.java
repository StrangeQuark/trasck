package com.strangequark.trasck.activity.storage;

public record AttachmentStorageWrite(
        String storageKey,
        String contentType,
        byte[] content
) {
}
