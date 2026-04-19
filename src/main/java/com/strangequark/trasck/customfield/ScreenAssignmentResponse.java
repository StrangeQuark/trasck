package com.strangequark.trasck.customfield;

import java.util.UUID;

public record ScreenAssignmentResponse(
        UUID id,
        UUID screenId,
        UUID projectId,
        UUID workItemTypeId,
        String operation,
        Integer priority
) {
    static ScreenAssignmentResponse from(ScreenAssignment assignment) {
        return new ScreenAssignmentResponse(
                assignment.getId(),
                assignment.getScreenId(),
                assignment.getProjectId(),
                assignment.getWorkItemTypeId(),
                assignment.getOperation(),
                assignment.getPriority()
        );
    }
}
