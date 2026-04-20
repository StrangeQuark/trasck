package com.strangequark.trasck.integration;

public record ImportJobRecordFieldDiffResponse(
        String path,
        String changeType,
        Object previousValue,
        Object value
) {
}
