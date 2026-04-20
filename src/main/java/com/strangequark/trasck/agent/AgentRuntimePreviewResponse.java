package com.strangequark.trasck.agent;

import java.util.List;
import java.util.UUID;

public record AgentRuntimePreviewResponse(
        UUID providerId,
        String providerKey,
        String providerType,
        String dispatchMode,
        String runtimeMode,
        String transport,
        Boolean externalExecutionEnabled,
        boolean valid,
        List<String> validationErrors,
        Object previewPayload
) {
}
