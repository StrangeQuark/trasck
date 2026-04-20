package com.strangequark.trasck.automation;

import java.util.UUID;

public record AutomationExecutionRequest(
        String sourceEntityType,
        UUID sourceEntityId,
        Object payload
) {
}
