package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportWorkspaceSettingsResponse(
        UUID workspaceId,
        Boolean sampleJobsEnabled,
        Boolean deploymentSampleJobsEnabled,
        Boolean sampleJobsAvailable,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static ImportWorkspaceSettingsResponse from(
            UUID workspaceId,
            ImportWorkspaceSettings settings,
            boolean defaultSampleJobsEnabled,
            boolean deploymentSampleJobsEnabled
    ) {
        boolean workspaceSampleJobsEnabled = settings == null
                ? defaultSampleJobsEnabled
                : Boolean.TRUE.equals(settings.getSampleJobsEnabled());
        return new ImportWorkspaceSettingsResponse(
                workspaceId,
                workspaceSampleJobsEnabled,
                deploymentSampleJobsEnabled,
                workspaceSampleJobsEnabled && deploymentSampleJobsEnabled,
                settings == null ? null : settings.getCreatedAt(),
                settings == null ? null : settings.getUpdatedAt()
        );
    }
}
