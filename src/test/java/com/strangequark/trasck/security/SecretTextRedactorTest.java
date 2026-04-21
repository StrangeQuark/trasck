package com.strangequark.trasck.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretTextRedactorTest {

    private final SecretTextRedactor redactor = new SecretTextRedactor();

    @Test
    void redactsCommonSecretShapesFromErrorText() {
        String value = """
                Authorization: Bearer trpat_secret-token
                password=correct-horse-battery-staple
                callbackPrivateKey=-----BEGIN PRIVATE KEY-----
                secret-key-body
                -----END PRIVATE KEY-----
                """;

        String redacted = redactor.redact(value);

        assertThat(redacted)
                .doesNotContain("trpat_secret-token")
                .doesNotContain("correct-horse-battery-staple")
                .doesNotContain("secret-key-body")
                .contains("[redacted]");
    }
}
