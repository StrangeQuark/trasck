package com.strangequark.trasck.workitem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WorkItemUpdateRequest(
        UUID typeId,
        String typeKey,
        UUID parentId,
        Boolean clearParent,
        UUID priorityId,
        String priorityKey,
        String title,
        String descriptionMarkdown,
        Object descriptionDocument,
        String visibility,
        BigDecimal estimatePoints,
        Integer estimateMinutes,
        Integer remainingMinutes,
        LocalDate startDate,
        LocalDate dueDate
) {
}
