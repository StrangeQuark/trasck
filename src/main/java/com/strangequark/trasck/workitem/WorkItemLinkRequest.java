package com.strangequark.trasck.workitem;

import java.util.UUID;

public record WorkItemLinkRequest(
        UUID targetWorkItemId,
        String linkType
) {
}
