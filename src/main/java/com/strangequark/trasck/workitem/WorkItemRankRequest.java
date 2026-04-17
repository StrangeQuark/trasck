package com.strangequark.trasck.workitem;

import java.util.UUID;

public record WorkItemRankRequest(
        UUID previousWorkItemId,
        UUID nextWorkItemId,
        UUID actorUserId
) {
}
