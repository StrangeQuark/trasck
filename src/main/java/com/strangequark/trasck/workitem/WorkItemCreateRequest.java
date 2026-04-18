package com.strangequark.trasck.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WorkItemCreateRequest(
        UUID typeId,
        String typeKey,
        UUID parentId,
        UUID statusId,
        String statusKey,
        UUID priorityId,
        String priorityKey,
        UUID assigneeId,
        UUID reporterId,
        String title,
        String descriptionMarkdown,
        JsonNode descriptionDocument,
        String visibility,
        BigDecimal estimatePoints,
        Integer estimateMinutes,
        Integer remainingMinutes,
        LocalDate startDate,
        LocalDate dueDate
) {
}
