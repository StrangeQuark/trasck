package com.strangequark.trasck.setup;

import java.util.List;
import java.util.UUID;

public record InitialSetupResponse(
        UserSummary adminUser
) {
    public record UserSummary(
            UUID id,
            String email,
            String username,
            String displayName,
            String accountType
    ) {
    }

    public record KeyedId(
            UUID id,
            String key,
            String name
    ) {
    }

    public record TypeRuleSummary(
            UUID id,
            String parentTypeKey,
            String childTypeKey
    ) {
    }

    public record WorkflowSummary(
            UUID id,
            String name,
            List<KeyedId> statuses,
            List<KeyedId> transitions
    ) {
    }

    public record BoardSummary(
            UUID id,
            String name,
            List<KeyedId> columns
    ) {
    }

    public record SeedDataSummary(
            List<KeyedId> workItemTypes,
            List<TypeRuleSummary> workItemTypeRules,
            List<KeyedId> priorities,
            List<KeyedId> resolutions,
            WorkflowSummary workflow,
            BoardSummary board,
            List<KeyedId> roles,
            List<KeyedId> projectWorkItemTypes,
            List<KeyedId> workflowAssignments,
            KeyedId projectSettings,
            KeyedId attachmentStorageConfig
    ) {
    }
}
