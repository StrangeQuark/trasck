package com.strangequark.trasck.planning;

import java.time.LocalDate;
import java.util.UUID;

public record RoadmapItemRequest(
        UUID workItemId,
        LocalDate startDate,
        LocalDate endDate,
        Integer position,
        Object displayConfig
) {
}
