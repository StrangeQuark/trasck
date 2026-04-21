package com.strangequark.trasck.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "workspace_security_policies")
public class WorkspaceSecurityPolicy {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "attachment_max_upload_bytes")
    private Long attachmentMaxUploadBytes;

    @Column(name = "attachment_max_download_bytes")
    private Long attachmentMaxDownloadBytes;

    @Column(name = "attachment_allowed_content_types")
    private String attachmentAllowedContentTypes;

    @Column(name = "export_max_artifact_bytes")
    private Long exportMaxArtifactBytes;

    @Column(name = "export_allowed_content_types")
    private String exportAllowedContentTypes;

    @Column(name = "import_max_parse_bytes")
    private Long importMaxParseBytes;

    @Column(name = "import_allowed_content_types")
    private String importAllowedContentTypes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getAttachmentMaxUploadBytes() {
        return attachmentMaxUploadBytes;
    }

    public void setAttachmentMaxUploadBytes(Long attachmentMaxUploadBytes) {
        this.attachmentMaxUploadBytes = attachmentMaxUploadBytes;
    }

    public Long getAttachmentMaxDownloadBytes() {
        return attachmentMaxDownloadBytes;
    }

    public void setAttachmentMaxDownloadBytes(Long attachmentMaxDownloadBytes) {
        this.attachmentMaxDownloadBytes = attachmentMaxDownloadBytes;
    }

    public String getAttachmentAllowedContentTypes() {
        return attachmentAllowedContentTypes;
    }

    public void setAttachmentAllowedContentTypes(String attachmentAllowedContentTypes) {
        this.attachmentAllowedContentTypes = attachmentAllowedContentTypes;
    }

    public Long getExportMaxArtifactBytes() {
        return exportMaxArtifactBytes;
    }

    public void setExportMaxArtifactBytes(Long exportMaxArtifactBytes) {
        this.exportMaxArtifactBytes = exportMaxArtifactBytes;
    }

    public String getExportAllowedContentTypes() {
        return exportAllowedContentTypes;
    }

    public void setExportAllowedContentTypes(String exportAllowedContentTypes) {
        this.exportAllowedContentTypes = exportAllowedContentTypes;
    }

    public Long getImportMaxParseBytes() {
        return importMaxParseBytes;
    }

    public void setImportMaxParseBytes(Long importMaxParseBytes) {
        this.importMaxParseBytes = importMaxParseBytes;
    }

    public String getImportAllowedContentTypes() {
        return importAllowedContentTypes;
    }

    public void setImportAllowedContentTypes(String importAllowedContentTypes) {
        this.importAllowedContentTypes = importAllowedContentTypes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
