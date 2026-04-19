package com.strangequark.trasck.team;

import java.util.UUID;

public record TeamRequest(
        String name,
        String description,
        UUID leadUserId,
        Integer defaultCapacity,
        String status
) {
}
