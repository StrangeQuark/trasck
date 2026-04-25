package com.strangequark.trasck.board;

import java.util.List;
import java.util.UUID;

public record BoardWorkItemsResponse(
        UUID boardId,
        UUID projectId,
        UUID iterationId,
        UUID teamId,
        String viewMode,
        int limitPerColumn,
        List<BoardColumnWorkItemsResponse> columns,
        List<BoardSwimlaneWorkItemsResponse> swimlanes
) {
}
