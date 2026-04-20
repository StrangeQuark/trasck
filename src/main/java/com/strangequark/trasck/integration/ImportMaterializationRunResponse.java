package com.strangequark.trasck.integration;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportMaterializationRunResponse(
        UUID id,
        UUID workspaceId,
        UUID importJobId,
        UUID mappingTemplateId,
        UUID transformPresetId,
        Integer transformPresetVersion,
        UUID projectId,
        UUID requestedById,
        Boolean updateExisting,
        Object mappingTemplateSnapshot,
        Object transformPresetSnapshot,
        Object transformationConfigSnapshot,
        String status,
        Integer recordsProcessed,
        Integer recordsCreated,
        Integer recordsUpdated,
        Integer recordsFailed,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
) {
    static ImportMaterializationRunResponse from(ImportMaterializationRun run) {
        return new ImportMaterializationRunResponse(
                run.getId(),
                run.getWorkspaceId(),
                run.getImportJobId(),
                run.getMappingTemplateId(),
                run.getTransformPresetId(),
                run.getTransformPresetVersion(),
                run.getProjectId(),
                run.getRequestedById(),
                run.getUpdateExisting(),
                JsonValues.toJavaValue(run.getMappingTemplateSnapshot()),
                JsonValues.toJavaValue(run.getTransformPresetSnapshot()),
                JsonValues.toJavaValue(run.getTransformationConfigSnapshot()),
                run.getStatus(),
                run.getRecordsProcessed(),
                run.getRecordsCreated(),
                run.getRecordsUpdated(),
                run.getRecordsFailed(),
                run.getCreatedAt(),
                run.getFinishedAt()
        );
    }
}
