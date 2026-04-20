package com.strangequark.trasck.planning;

import java.time.LocalDate;
import java.util.UUID;

public record ReleaseResponse(
        UUID id,
        UUID projectId,
        String name,
        String version,
        LocalDate startDate,
        LocalDate releaseDate,
        String status,
        String description
) {
    static ReleaseResponse from(Release release) {
        return new ReleaseResponse(
                release.getId(),
                release.getProjectId(),
                release.getName(),
                release.getVersion(),
                release.getStartDate(),
                release.getReleaseDate(),
                release.getStatus(),
                release.getDescription()
        );
    }
}
