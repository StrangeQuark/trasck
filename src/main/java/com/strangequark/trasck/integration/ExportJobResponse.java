package com.strangequark.trasck.integration;

import com.strangequark.trasck.activity.Attachment;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportJobResponse(
        UUID id,
        UUID workspaceId,
        UUID requestedById,
        String exportType,
        String status,
        UUID fileAttachmentId,
        String filename,
        String contentType,
        Long sizeBytes,
        String checksum,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
    public static ExportJobResponse from(ExportJob job, Attachment attachment) {
        return new ExportJobResponse(
                job.getId(),
                job.getWorkspaceId(),
                job.getRequestedById(),
                job.getExportType(),
                job.getStatus(),
                job.getFileAttachmentId(),
                attachment == null ? null : attachment.getFilename(),
                attachment == null ? null : attachment.getContentType(),
                attachment == null ? null : attachment.getSizeBytes(),
                attachment == null ? null : attachment.getChecksum(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}
