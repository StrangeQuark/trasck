package com.strangequark.trasck.workitem;

import java.util.UUID;

public record AttachmentMetadataRequest(
        UUID storageConfigId,
        String filename,
        String contentType,
        String storageKey,
        Long sizeBytes,
        String checksum,
        String visibility
) {
}
