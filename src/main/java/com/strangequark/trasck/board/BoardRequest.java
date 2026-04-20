package com.strangequark.trasck.board;

import java.util.UUID;

public record BoardRequest(
        String name,
        String type,
        UUID teamId,
        Object filterConfig,
        Boolean active
) {
}
