package com.strangequark.trasck.board;

import java.util.UUID;

public record BoardSwimlaneRequest(
        String name,
        String swimlaneType,
        UUID savedFilterId,
        Boolean clearSavedFilter,
        Object query,
        Integer position,
        Boolean enabled
) {
}
