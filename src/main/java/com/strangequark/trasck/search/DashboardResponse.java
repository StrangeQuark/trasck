package com.strangequark.trasck.search;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        UUID id,
        UUID workspaceId,
        UUID ownerId,
        UUID teamId,
        String name,
        String visibility,
        Object layout,
        List<DashboardWidgetResponse> widgets,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static DashboardResponse from(Dashboard dashboard, List<DashboardWidget> widgets) {
        return new DashboardResponse(
                dashboard.getId(),
                dashboard.getWorkspaceId(),
                dashboard.getOwnerId(),
                dashboard.getTeamId(),
                dashboard.getName(),
                dashboard.getVisibility(),
                JsonValues.toJavaValue(dashboard.getLayout()),
                widgets.stream().map(DashboardWidgetResponse::from).toList(),
                dashboard.getCreatedAt(),
                dashboard.getUpdatedAt()
        );
    }
}
