package com.strangequark.trasck.automation;

import java.util.UUID;

public record AutomationRuleRequest(
        UUID projectId,
        String name,
        String triggerType,
        Object triggerConfig,
        Boolean enabled
) {
}
