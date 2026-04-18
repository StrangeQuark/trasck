package com.strangequark.trasck.workitem;

import com.strangequark.trasck.activity.Watcher;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WatcherResponse(
        UUID workItemId,
        UUID userId,
        OffsetDateTime createdAt
) {
    static WatcherResponse from(Watcher watcher) {
        return new WatcherResponse(
                watcher.getId().getWorkItemId(),
                watcher.getId().getUserId(),
                watcher.getCreatedAt()
        );
    }
}
