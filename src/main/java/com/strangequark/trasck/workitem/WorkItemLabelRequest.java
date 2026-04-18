package com.strangequark.trasck.workitem;

import java.util.UUID;

public record WorkItemLabelRequest(
        UUID labelId,
        String name,
        String color
) {
}
