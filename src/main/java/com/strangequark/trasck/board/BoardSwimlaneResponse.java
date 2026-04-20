package com.strangequark.trasck.board;

import com.strangequark.trasck.JsonValues;
import java.util.UUID;

public record BoardSwimlaneResponse(
        UUID id,
        UUID boardId,
        String name,
        String swimlaneType,
        UUID savedFilterId,
        Object query,
        Integer position,
        Boolean enabled
) {
    static BoardSwimlaneResponse from(BoardSwimlane swimlane) {
        return new BoardSwimlaneResponse(
                swimlane.getId(),
                swimlane.getBoardId(),
                swimlane.getName(),
                swimlane.getSwimlaneType(),
                swimlane.getSavedFilterId(),
                JsonValues.toJavaValue(swimlane.getQuery()),
                swimlane.getPosition(),
                swimlane.getEnabled()
        );
    }
}
