package com.strangequark.trasck.security;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SecretTextRedactor {

    private static final String REDACTED = "[redacted]";
    private static final List<Pattern> SECRET_ASSIGNMENTS = List.of(
            Pattern.compile("(?i)(\\b(?:password|passwd|secret|token|api[_-]?key|private[_-]?key|credential)\\b\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&]+)"),
            Pattern.compile("(?i)(\\bAuthorization\\b\\s*[:=]\\s*Bearer\\s+)[^\\s,;&]+"),
            Pattern.compile("(?is)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----")
    );

    public String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = value;
        redacted = SECRET_ASSIGNMENTS.get(0).matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = SECRET_ASSIGNMENTS.get(1).matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = SECRET_ASSIGNMENTS.get(2).matcher(redacted).replaceAll(REDACTED);
        return redacted;
    }
}
