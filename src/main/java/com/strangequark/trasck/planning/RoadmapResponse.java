package com.strangequark.trasck.planning;

import com.strangequark.trasck.JsonValues;
import java.util.List;
import java.util.UUID;

public record RoadmapResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        String name,
        Object config,
        UUID ownerId,
        String visibility,
        List<RoadmapItemResponse> items
) {
    static RoadmapResponse from(Roadmap roadmap, List<RoadmapItem> items) {
        return new RoadmapResponse(
                roadmap.getId(),
                roadmap.getWorkspaceId(),
                roadmap.getProjectId(),
                roadmap.getName(),
                JsonValues.toJavaValue(roadmap.getConfig()),
                roadmap.getOwnerId(),
                roadmap.getVisibility(),
                items.stream().map(RoadmapItemResponse::from).toList()
        );
    }
}
