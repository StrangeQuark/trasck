package com.strangequark.trasck.planning;

import java.math.BigDecimal;
import java.util.UUID;

public record IterationCloseRequest(
        BigDecimal completedPoints,
        Boolean carryOverIncomplete,
        UUID carryOverIterationId
) {
}
