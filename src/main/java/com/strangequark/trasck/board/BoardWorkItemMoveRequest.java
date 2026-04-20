package com.strangequark.trasck.board;

import java.util.UUID;

public record BoardWorkItemMoveRequest(
        String transitionKey,
        UUID targetColumnId,
        UUID targetStatusId,
        UUID previousWorkItemId,
        UUID nextWorkItemId
) {
}
