package com.strangequark.trasck.board;

public record BoardSwimlaneRequest(
        String name,
        String swimlaneType,
        Object query,
        Integer position,
        Boolean enabled
) {
}
