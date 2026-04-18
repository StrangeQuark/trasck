package com.strangequark.trasck.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DomainEventPayloadRedactor {

    private final ObjectMapper objectMapper;

    public DomainEventPayloadRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode redact(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (payload.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            for (var field : payload.properties()) {
                result.set(field.getKey(), shouldRedact(field.getKey()) ? objectMapper.getNodeFactory().textNode("[REDACTED]") : redact(field.getValue()));
            }
            return result;
        }
        if (payload.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            payload.forEach(value -> result.add(redact(value)));
            return result;
        }
        return payload.deepCopy();
    }

    private boolean shouldRedact(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.equals("token")
                || normalized.equals("rawtoken")
                || normalized.equals("accesstoken")
                || normalized.equals("refreshtoken")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("credential")
                || normalized.contains("assertion")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("hash");
    }
}
