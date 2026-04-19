package com.strangequark.trasck.search;

import com.strangequark.trasck.JsonValues;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardWidgetResponse(
        UUID id,
        UUID dashboardId,
        String widgetType,
        String title,
        Object config,
        Integer positionX,
        Integer positionY,
        Integer width,
        Integer height,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    static DashboardWidgetResponse from(DashboardWidget widget) {
        return new DashboardWidgetResponse(
                widget.getId(),
                widget.getDashboardId(),
                widget.getWidgetType(),
                widget.getTitle(),
                JsonValues.toJavaValue(widget.getConfig()),
                widget.getPositionX(),
                widget.getPositionY(),
                widget.getWidth(),
                widget.getHeight(),
                widget.getCreatedAt(),
                widget.getUpdatedAt()
        );
    }
}
