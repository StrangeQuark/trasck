package com.strangequark.trasck.board;

import com.strangequark.trasck.JsonValues;
import java.util.UUID;

public record BoardColumnResponse(
        UUID id,
        UUID boardId,
        String name,
        Object statusIds,
        Integer position,
        Integer wipLimit,
        Boolean doneColumn
) {
    static BoardColumnResponse from(BoardColumn column) {
        return new BoardColumnResponse(
                column.getId(),
                column.getBoardId(),
                column.getName(),
                JsonValues.toJavaValue(column.getStatusIds()),
                column.getPosition(),
                column.getWipLimit(),
                column.getDoneColumn()
        );
    }
}
