package com.strangequark.trasck.board;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BoardResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        UUID teamId,
        String name,
        String type,
        Object filterConfig,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version,
        List<BoardColumnResponse> columns,
        List<BoardSwimlaneResponse> swimlanes
) {
    static BoardResponse from(Board board, List<BoardColumn> columns, List<BoardSwimlane> swimlanes) {
        return new BoardResponse(
                board.getId(),
                board.getWorkspaceId(),
                board.getProjectId(),
                board.getTeamId(),
                board.getName(),
                board.getType(),
                JsonValues.toJavaValue(board.getFilterConfig()),
                board.getActive(),
                board.getCreatedAt(),
                board.getUpdatedAt(),
                board.getVersion(),
                columns.stream().map(BoardColumnResponse::from).toList(),
                swimlanes.stream().map(BoardSwimlaneResponse::from).toList()
        );
    }
}
