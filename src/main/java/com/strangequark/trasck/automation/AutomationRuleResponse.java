package com.strangequark.trasck.automation;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AutomationRuleResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String name,
        String triggerType,
        Object triggerConfig,
        Boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<AutomationConditionResponse> conditions,
        List<AutomationActionResponse> actions
) {
    static AutomationRuleResponse from(AutomationRule rule, List<AutomationCondition> conditions, List<AutomationAction> actions) {
        return new AutomationRuleResponse(
                rule.getId(),
                rule.getWorkspaceId(),
                rule.getProjectId(),
                rule.getName(),
                rule.getTriggerType(),
                JsonValues.toJavaValue(rule.getTriggerConfig()),
                rule.getEnabled(),
                rule.getCreatedAt(),
                rule.getUpdatedAt(),
                conditions.stream().map(AutomationConditionResponse::from).toList(),
                actions.stream().map(AutomationActionResponse::from).toList()
        );
    }
}
