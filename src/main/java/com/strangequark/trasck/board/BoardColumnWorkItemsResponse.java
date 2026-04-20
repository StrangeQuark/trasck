package com.strangequark.trasck.board;

import com.strangequark.trasck.workitem.WorkItemResponse;
import java.util.List;
import java.util.UUID;

public record BoardColumnWorkItemsResponse(
        UUID columnId,
        String columnName,
        List<UUID> statusIds,
        List<WorkItemResponse> workItems
) {
}
