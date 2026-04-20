package com.strangequark.trasck.planning;

import java.util.UUID;

public record RoadmapRequest(
        UUID projectId,
        String name,
        Object config,
        String visibility
) {
}
