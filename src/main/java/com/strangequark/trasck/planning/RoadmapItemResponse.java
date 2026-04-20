package com.strangequark.trasck.planning;

import com.strangequark.trasck.JsonValues;
import java.time.LocalDate;
import java.util.UUID;

public record RoadmapItemResponse(
        UUID id,
        UUID roadmapId,
        UUID workItemId,
        LocalDate startDate,
        LocalDate endDate,
        Integer position,
        Object displayConfig
) {
    static RoadmapItemResponse from(RoadmapItem item) {
        return new RoadmapItemResponse(
                item.getId(),
                item.getRoadmapId(),
                item.getWorkItemId(),
                item.getStartDate(),
                item.getEndDate(),
                item.getPosition(),
                JsonValues.toJavaValue(item.getDisplayConfig())
        );
    }
}
