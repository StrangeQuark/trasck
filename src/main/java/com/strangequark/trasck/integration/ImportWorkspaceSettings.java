package com.strangequark.trasck.integration;

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
@Table(name = "import_workspace_settings")
public class ImportWorkspaceSettings {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "sample_jobs_enabled")
    private Boolean sampleJobsEnabled;

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

    public Boolean getSampleJobsEnabled() {
        return sampleJobsEnabled;
    }

    public void setSampleJobsEnabled(Boolean sampleJobsEnabled) {
        this.sampleJobsEnabled = sampleJobsEnabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
