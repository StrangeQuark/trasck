package com.strangequark.trasck.automation;

public record AutomationActionRequest(
        String actionType,
        String executionMode,
        Object config,
        Integer position
) {
}
