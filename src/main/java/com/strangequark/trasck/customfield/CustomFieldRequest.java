package com.strangequark.trasck.customfield;

public record CustomFieldRequest(
        String name,
        String key,
        String fieldType,
        Object options,
        Boolean searchable,
        Boolean archived
) {
}
