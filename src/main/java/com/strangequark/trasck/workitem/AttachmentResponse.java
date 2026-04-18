package com.strangequark.trasck.workitem;

import com.strangequark.trasck.activity.Attachment;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        UUID workspaceId,
        UUID storageConfigId,
        UUID uploaderId,
        String filename,
        String contentType,
        String storageKey,
        Long sizeBytes,
        String checksum,
        String visibility,
        OffsetDateTime createdAt
) {
    static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getWorkspaceId(),
                attachment.getStorageConfigId(),
                attachment.getUploaderId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getStorageKey(),
                attachment.getSizeBytes(),
                attachment.getChecksum(),
                attachment.getVisibility(),
                attachment.getCreatedAt()
        );
    }
}
