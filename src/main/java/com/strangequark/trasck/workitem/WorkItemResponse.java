package com.strangequark.trasck.workitem;

import com.strangequark.trasck.JsonValues;
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
        UUID teamId,
        UUID assigneeId,
        UUID reporterId,
        String key,
        Long sequenceNumber,
        Long workspaceSequenceNumber,
        String title,
        String descriptionMarkdown,
        Object descriptionDocument,
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
    public static WorkItemResponse from(WorkItem item) {
        return new WorkItemResponse(
                item.getId(),
                item.getWorkspaceId(),
                item.getProjectId(),
                item.getTypeId(),
                item.getParentId(),
                item.getStatusId(),
                item.getPriorityId(),
                item.getResolutionId(),
                item.getTeamId(),
                item.getAssigneeId(),
                item.getReporterId(),
                item.getKey(),
                item.getSequenceNumber(),
                item.getWorkspaceSequenceNumber(),
                item.getTitle(),
                item.getDescriptionMarkdown(),
                JsonValues.toJavaValue(item.getDescriptionDocument()),
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
