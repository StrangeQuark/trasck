package com.strangequark.trasck.workitem;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LabelResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String color,
        OffsetDateTime createdAt
) {
    static LabelResponse from(Label label) {
        return new LabelResponse(
                label.getId(),
                label.getWorkspaceId(),
                label.getName(),
                label.getColor(),
                label.getCreatedAt()
        );
    }
}
