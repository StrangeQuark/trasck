package com.strangequark.trasck.workitem;

import java.util.UUID;

public record WorkItemTransitionRequest(
        String transitionKey,
        UUID actorUserId
) {
}
