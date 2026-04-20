package com.strangequark.trasck.board;

import java.util.List;
import java.util.UUID;

public record BoardSwimlaneWorkItemsResponse(
        UUID swimlaneId,
        String swimlaneName,
        String swimlaneType,
        List<BoardColumnWorkItemsResponse> columns
) {
}
