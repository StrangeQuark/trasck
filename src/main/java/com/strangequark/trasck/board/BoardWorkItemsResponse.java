package com.strangequark.trasck.board;

import java.util.List;
import java.util.UUID;

public record BoardWorkItemsResponse(
        UUID boardId,
        UUID projectId,
        int limitPerColumn,
        List<BoardColumnWorkItemsResponse> columns
) {
}
