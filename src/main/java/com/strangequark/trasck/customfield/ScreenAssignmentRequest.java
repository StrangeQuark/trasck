package com.strangequark.trasck.customfield;

import java.util.UUID;

public record ScreenAssignmentRequest(
        UUID projectId,
        UUID workItemTypeId,
        String operation,
        Integer priority
) {
}
