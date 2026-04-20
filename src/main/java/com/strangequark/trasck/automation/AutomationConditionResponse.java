package com.strangequark.trasck.automation;

import com.strangequark.trasck.JsonValues;
import java.util.UUID;

public record AutomationConditionResponse(
        UUID id,
        UUID ruleId,
        String conditionType,
        Object config,
        Integer position
) {
    static AutomationConditionResponse from(AutomationCondition condition) {
        return new AutomationConditionResponse(
                condition.getId(),
                condition.getRuleId(),
                condition.getConditionType(),
                JsonValues.toJavaValue(condition.getConfig()),
                condition.getPosition()
        );
    }
}
