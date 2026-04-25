package com.strangequark.trasck.board;

import com.strangequark.trasck.workflow.WorkflowStatus;
import java.util.UUID;

public record BoardStatusOptionResponse(
        UUID id,
        UUID workflowId,
        String key,
        String name,
        String category,
        String color,
        Integer sortOrder,
        Boolean terminal
) {
    static BoardStatusOptionResponse from(WorkflowStatus status) {
        return new BoardStatusOptionResponse(
                status.getId(),
                status.getWorkflowId(),
                status.getKey(),
                status.getName(),
                status.getCategory(),
                status.getColor(),
                status.getSortOrder(),
                status.getTerminal()
        );
    }
}
