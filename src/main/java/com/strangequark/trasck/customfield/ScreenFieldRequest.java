package com.strangequark.trasck.customfield;

import java.util.UUID;

public record ScreenFieldRequest(
        UUID customFieldId,
        String systemFieldKey,
        Integer position,
        Boolean required
) {
}
