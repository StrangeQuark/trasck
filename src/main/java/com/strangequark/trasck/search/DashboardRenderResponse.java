package com.strangequark.trasck.search;

import java.time.OffsetDateTime;
import java.util.List;

public record DashboardRenderResponse(
        DashboardResponse dashboard,
        OffsetDateTime from,
        OffsetDateTime to,
        List<DashboardWidgetRenderResponse> widgets
) {
}
