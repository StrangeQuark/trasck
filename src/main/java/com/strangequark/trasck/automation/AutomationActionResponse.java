package com.strangequark.trasck.automation;

import com.strangequark.trasck.JsonValues;
import java.util.UUID;

public record AutomationActionResponse(
        UUID id,
        UUID ruleId,
        String actionType,
        String executionMode,
        Object config,
        Integer position
) {
    static AutomationActionResponse from(AutomationAction action) {
        return new AutomationActionResponse(
                action.getId(),
                action.getRuleId(),
                action.getActionType(),
                action.getExecutionMode(),
                JsonValues.toJavaValue(action.getConfig()),
                action.getPosition()
        );
    }
}
