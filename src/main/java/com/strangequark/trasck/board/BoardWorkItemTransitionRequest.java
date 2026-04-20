package com.strangequark.trasck.board;

import java.util.UUID;

public record BoardWorkItemTransitionRequest(
        String transitionKey,
        UUID targetColumnId,
        UUID targetStatusId
) {
}
