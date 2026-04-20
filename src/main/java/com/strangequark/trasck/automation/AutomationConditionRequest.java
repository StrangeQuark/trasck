package com.strangequark.trasck.automation;

public record AutomationConditionRequest(
        String conditionType,
        Object config,
        Integer position
) {
}
