package com.strangequark.trasck.search;

import com.strangequark.trasck.JsonValues;
import java.util.UUID;

public record SavedViewResponse(
        UUID id,
        UUID workspaceId,
        UUID ownerId,
        String name,
        String viewType,
        Object config,
        String visibility
) {
    static SavedViewResponse from(SavedView view) {
        return new SavedViewResponse(
                view.getId(),
                view.getWorkspaceId(),
                view.getOwnerId(),
                view.getName(),
                view.getViewType(),
                JsonValues.toJavaValue(view.getConfig()),
                view.getVisibility()
        );
    }
}
