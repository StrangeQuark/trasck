package com.strangequark.trasck.customfield;

import java.util.List;
import java.util.UUID;

public record CustomFieldSearchFilter(
        UUID customFieldId,
        String fieldKey,
        String fieldType,
        String operator,
        List<String> values
) {
}
