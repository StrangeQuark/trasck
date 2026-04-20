package com.strangequark.trasck.board;

public record BoardColumnRequest(
        String name,
        Object statusIds,
        Integer position,
        Integer wipLimit,
        Boolean doneColumn
) {
}
