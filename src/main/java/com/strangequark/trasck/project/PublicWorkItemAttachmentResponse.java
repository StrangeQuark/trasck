package com.strangequark.trasck.project;

import com.strangequark.trasck.activity.Attachment;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicWorkItemAttachmentResponse(
        UUID id,
        UUID workItemId,
        String filename,
        String contentType,
        Long sizeBytes,
        String checksum,
        OffsetDateTime createdAt,
        String downloadUrl,
        OffsetDateTime downloadUrlExpiresAt
) {
    static PublicWorkItemAttachmentResponse from(
            UUID projectId,
            UUID workItemId,
            Attachment attachment,
            PublicAttachmentDownloadTokenService.SignedDownload signedDownload
    ) {
        String downloadUrl = "/api/v1/public/projects/"
                + projectId
                + "/work-items/"
                + workItemId
                + "/attachments/"
                + attachment.getId()
                + "/download?token="
                + signedDownload.token();
        return new PublicWorkItemAttachmentResponse(
                attachment.getId(),
                workItemId,
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getChecksum(),
                attachment.getCreatedAt(),
                downloadUrl,
                signedDownload.expiresAt()
        );
    }
}
