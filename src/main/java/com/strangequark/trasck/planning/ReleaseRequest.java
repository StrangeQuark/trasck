package com.strangequark.trasck.planning;

import java.time.LocalDate;

public record ReleaseRequest(
        String name,
        String version,
        LocalDate startDate,
        LocalDate releaseDate,
        String status,
        String description
) {
}
