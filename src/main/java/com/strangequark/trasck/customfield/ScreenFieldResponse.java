package com.strangequark.trasck.customfield;

import java.util.UUID;

public record ScreenFieldResponse(
        UUID id,
        UUID screenId,
        UUID customFieldId,
        String systemFieldKey,
        Integer position,
        Boolean required
) {
    static ScreenFieldResponse from(ScreenField field) {
        return new ScreenFieldResponse(
                field.getId(),
                field.getScreenId(),
                field.getCustomFieldId(),
                field.getSystemFieldKey(),
                field.getPosition(),
                field.getRequired()
        );
    }
}
