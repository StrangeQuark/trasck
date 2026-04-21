package com.strangequark.trasck.project;

import com.strangequark.trasck.JsonValues;
import com.strangequark.trasck.workitem.WorkItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicWorkItemResponse(
        UUID id,
        UUID projectId,
        UUID typeId,
        UUID parentId,
        UUID statusId,
        UUID priorityId,
        UUID teamId,
        String key,
        String title,
        String descriptionMarkdown,
        Object descriptionDocument,
        String visibility,
        BigDecimal estimatePoints,
        LocalDate startDate,
        LocalDate dueDate,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static PublicWorkItemResponse from(WorkItem item) {
        return new PublicWorkItemResponse(
                item.getId(),
                item.getProjectId(),
                item.getTypeId(),
                item.getParentId(),
                item.getStatusId(),
                item.getPriorityId(),
                item.getTeamId(),
                item.getKey(),
                item.getTitle(),
                item.getDescriptionMarkdown(),
                JsonValues.toJavaValue(item.getDescriptionDocument()),
                item.getVisibility(),
                item.getEstimatePoints(),
                item.getStartDate(),
                item.getDueDate(),
                item.getResolvedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
