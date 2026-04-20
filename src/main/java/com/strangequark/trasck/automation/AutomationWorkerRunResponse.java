package com.strangequark.trasck.automation;

import java.util.List;
import java.util.UUID;

public record AutomationWorkerRunResponse(
        UUID workspaceId,
        int processed,
        int succeeded,
        int failed,
        List<AutomationExecutionJobResponse> jobs
) {
}
