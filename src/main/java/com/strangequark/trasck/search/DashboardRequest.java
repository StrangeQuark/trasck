package com.strangequark.trasck.search;

import java.util.UUID;

public record DashboardRequest(
        String name,
        String visibility,
        UUID teamId,
        Object layout
) {
}
