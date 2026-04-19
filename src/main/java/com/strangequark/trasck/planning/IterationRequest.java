package com.strangequark.trasck.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IterationRequest(
        String name,
        UUID teamId,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        BigDecimal committedPoints,
        BigDecimal completedPoints
) {
}
