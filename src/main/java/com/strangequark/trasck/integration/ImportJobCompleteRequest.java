package com.strangequark.trasck.integration;

public record ImportJobCompleteRequest(
        Boolean acceptOpenConflicts,
        String openConflictConfirmation,
        String openConflictReason
) {
}
