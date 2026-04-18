package com.strangequark.trasck.workitem;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkItemLinkResponse(
        UUID id,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        String linkType,
        UUID createdById,
        OffsetDateTime createdAt
) {
    static WorkItemLinkResponse from(WorkItemLink link) {
        return new WorkItemLinkResponse(
                link.getId(),
                link.getSourceWorkItemId(),
                link.getTargetWorkItemId(),
                link.getLinkType(),
                link.getCreatedById(),
                link.getCreatedAt()
        );
    }
}
