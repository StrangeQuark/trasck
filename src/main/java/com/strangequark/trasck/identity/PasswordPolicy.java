package com.strangequark.trasck.identity;

import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PasswordPolicy {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password",
            "password123",
            "trasck",
            "trasck-password",
            "admin123456",
            "changeme123456"
    );

    private final int minLength;

    public PasswordPolicy(@Value("${trasck.security.password-min-length:12}") int minLength) {
        this.minLength = minLength;
    }

    public void validateNewPassword(String password, String fieldName) {
        if (password == null || password.isBlank()) {
            throw badRequest(fieldName + " is required");
        }
        if (password.length() < minLength) {
            throw badRequest(fieldName + " must be at least " + minLength + " characters");
        }
        if (COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw badRequest(fieldName + " is too common");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
