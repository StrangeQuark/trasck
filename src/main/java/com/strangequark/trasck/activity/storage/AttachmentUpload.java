package com.strangequark.trasck.activity.storage;

public record AttachmentUpload(
        String filename,
        String contentType,
        byte[] content,
        String checksum
) {
}
