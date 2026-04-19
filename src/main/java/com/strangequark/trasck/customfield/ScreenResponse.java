package com.strangequark.trasck.customfield;

import com.strangequark.trasck.JsonValues;
import java.util.List;
import java.util.UUID;

public record ScreenResponse(
        UUID id,
        UUID workspaceId,
        String name,
        String screenType,
        Object config,
        List<ScreenFieldResponse> fields,
        List<ScreenAssignmentResponse> assignments
) {
    static ScreenResponse from(Screen screen, List<ScreenField> fields, List<ScreenAssignment> assignments) {
        return new ScreenResponse(
                screen.getId(),
                screen.getWorkspaceId(),
                screen.getName(),
                screen.getScreenType(),
                JsonValues.toJavaValue(screen.getConfig()),
                fields.stream().map(ScreenFieldResponse::from).toList(),
                assignments.stream().map(ScreenAssignmentResponse::from).toList()
        );
    }
}
