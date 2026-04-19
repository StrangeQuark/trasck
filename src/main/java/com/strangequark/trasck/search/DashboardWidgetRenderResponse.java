package com.strangequark.trasck.search;

import java.util.UUID;

public record DashboardWidgetRenderResponse(
        UUID widgetId,
        String widgetType,
        String title,
        Object data
) {
}
