package com.strangequark.trasck.workitem;

import java.util.UUID;

public record WorkItemAssignRequest(
        UUID assigneeId
) {
}
