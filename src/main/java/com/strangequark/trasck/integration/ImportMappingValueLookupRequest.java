package com.strangequark.trasck.integration;

public record ImportMappingValueLookupRequest(
        String sourceField,
        String sourceValue,
        String targetField,
        Object targetValue,
        Integer sortOrder,
        Boolean enabled
) {
}
