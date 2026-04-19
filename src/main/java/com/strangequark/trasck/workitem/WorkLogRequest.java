package com.strangequark.trasck.workitem;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkLogRequest(
        UUID userId,
        Integer minutesSpent,
        LocalDate workDate,
        OffsetDateTime startedAt,
        String descriptionMarkdown,
        Object descriptionDocument
) {
}
