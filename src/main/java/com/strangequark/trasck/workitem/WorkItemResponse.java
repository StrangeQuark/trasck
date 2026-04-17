package com.strangequark.trasck.workitem;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkItemResponse(
        UUID id,
        UUID workspaceId,
        UUID projectId,
        UUID typeId,
        UUID parentId,
        UUID statusId,
        UUID priorityId,
        UUID resolutionId,
        UUID assigneeId,
        UUID reporterId,
        String key,
        Long sequenceNumber,
        Long workspaceSequenceNumber,
        String title,
        String descriptionMarkdown,
        JsonNode descriptionDocument,
        String visibility,
        BigDecimal estimatePoints,
        Integer estimateMinutes,
        Integer remainingMinutes,
        String rank,
        LocalDate startDate,
        LocalDate dueDate,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt
) {
    static WorkItemResponse from(WorkItem item) {
        return new WorkItemResponse(
                item.getId(),
                item.getWorkspaceId(),
                item.getProjectId(),
                item.getTypeId(),
                item.getParentId(),
                item.getStatusId(),
                item.getPriorityId(),
                item.getResolutionId(),
                item.getAssigneeId(),
                item.getReporterId(),
                item.getKey(),
                item.getSequenceNumber(),
                item.getWorkspaceSequenceNumber(),
                item.getTitle(),
                item.getDescriptionMarkdown(),
                item.getDescriptionDocument(),
                item.getVisibility(),
                item.getEstimatePoints(),
                item.getEstimateMinutes(),
                item.getRemainingMinutes(),
                item.getRank(),
                item.getStartDate(),
                item.getDueDate(),
                item.getResolvedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getDeletedAt()
        );
    }
}
