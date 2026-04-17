package com.strangequark.trasck.setup;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class SetupRequestValidator {

    private SetupRequestValidator() {
    }

    static <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value;
    }

    static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    static String optionalText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    static String key(String value, String fieldName) {
        String normalized = requiredText(value, fieldName).replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must include at least one letter or digit");
        }
        return normalized;
    }

    static String slug(String value, String fieldName) {
        String normalized = requiredText(value, fieldName)
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must include at least one letter or digit");
        }
        return normalized;
    }
}
