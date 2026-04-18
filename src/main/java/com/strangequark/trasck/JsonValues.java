package com.strangequark.trasck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonValues {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonValues() {
    }

    public static Object toJavaValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(node, Object.class);
    }
}
