package com.strangequark.trasck.planning;

import java.math.BigDecimal;

public record IterationCommitRequest(
        BigDecimal committedPoints,
        Boolean activate
) {
}
