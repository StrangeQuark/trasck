package com.strangequark.trasck.search;

import java.util.UUID;

public record ReportQueryCatalogRequest(
        String queryKey,
        String name,
        String description,
        String queryType,
        Object queryConfig,
        Object parametersSchema,
        String visibility,
        UUID projectId,
        UUID teamId,
        Boolean enabled
) {
}
