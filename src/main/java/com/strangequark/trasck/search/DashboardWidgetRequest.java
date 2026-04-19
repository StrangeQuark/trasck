package com.strangequark.trasck.search;

public record DashboardWidgetRequest(
        String widgetType,
        String title,
        Object config,
        Integer positionX,
        Integer positionY,
        Integer width,
        Integer height
) {
}
