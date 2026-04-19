package com.strangequark.trasck.workitem;

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
        UUID teamId,
        UUID assigneeId,
        UUID reporterId,
        String title,
        String descriptionMarkdown,
        Object descriptionDocument,
        String visibility,
        BigDecimal estimatePoints,
        Integer estimateMinutes,
        Integer remainingMinutes,
        LocalDate startDate,
        LocalDate dueDate,
        Object customFields
) {
}
