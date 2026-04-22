package com.strangequark.trasck.agent;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AgentCliWorkerProperties {

    private static final int DEFAULT_MAX_OUTPUT_BYTES = 524_288;

    private final Environment environment;

    public AgentCliWorkerProperties(Environment environment) {
        this.environment = environment;
    }

    boolean enabled() {
        return Boolean.parseBoolean(environment.getProperty("trasck.agents.cli-worker.enabled", "false"));
    }

    int maxConcurrency() {
        return positiveInt(environment.getProperty("trasck.agents.cli-worker.max-concurrency"), 2);
    }

    int maxOutputBytes() {
        return positiveInt(environment.getProperty("trasck.agents.cli-worker.max-output-bytes"), DEFAULT_MAX_OUTPUT_BYTES);
    }

    Path workspaceRoot() {
        return Path.of(environment.getProperty("trasck.agents.cli-worker.workspace-root", "./data/agent-cli-runs"))
                .toAbsolutePath()
                .normalize();
    }

    Duration defaultTimeout() {
        return duration(environment.getProperty("trasck.agents.cli-worker.timeout", "PT30M"));
    }

    Optional<CommandProfile> profile(String providerType, String requestedProfileName) {
        String profileName = canonicalProfileName(providerType, requestedProfileName);
        if (profileName == null) {
            return Optional.empty();
        }
        String prefix = "trasck.agents.cli-worker.profiles." + profileName + ".";
        String command = trimToNull(environment.getProperty(prefix + "command", defaultCommand(profileName)));
        if (command == null) {
            return Optional.empty();
        }
        return Optional.of(new CommandProfile(
                profileName,
                providerTypeForProfile(profileName),
                command,
                splitPipe(firstText(environment.getProperty(prefix + "arguments"), defaultArguments(profileName))),
                trimToNull(environment.getProperty(prefix + "working-directory")),
                Boolean.parseBoolean(environment.getProperty(prefix + "send-prompt-to-stdin", defaultSendPromptToStdin(profileName))),
                splitCsv(firstText(environment.getProperty(prefix + "credential-types"), defaultCredentialTypes(profileName))),
                duration(environment.getProperty(prefix + "timeout", defaultTimeout().toString()))
        ));
    }

    private String canonicalProfileName(String providerType, String requestedProfileName) {
        String normalizedProvider = normalize(providerType);
        String normalizedProfile = normalize(firstText(requestedProfileName, defaultProfileName(normalizedProvider)));
        if ("codex".equals(normalizedProvider) && List.of("codex", "default", "codex-default", "codex-local").contains(normalizedProfile)) {
            return "codex-local";
        }
        if ("claude-code".equals(normalizedProvider) && List.of("claude", "claude-code", "default", "claude-default", "claude-code-local").contains(normalizedProfile)) {
            return "claude-code-local";
        }
        if (List.of("codex-local", "claude-code-local").contains(normalizedProfile)) {
            return normalizedProfile;
        }
        return null;
    }

    private String defaultProfileName(String providerType) {
        return switch (providerType) {
            case "codex" -> "codex-local";
            case "claude-code" -> "claude-code-local";
            default -> null;
        };
    }

    private String providerTypeForProfile(String profileName) {
        return switch (profileName) {
            case "codex-local" -> "codex";
            case "claude-code-local" -> "claude_code";
            default -> "";
        };
    }

    private String defaultCommand(String profileName) {
        return switch (profileName) {
            case "codex-local" -> "codex";
            case "claude-code-local" -> "claude";
            default -> "";
        };
    }

    private String defaultArguments(String profileName) {
        return switch (profileName) {
            case "codex-local" -> "exec|--full-auto|-";
            case "claude-code-local" -> "-p|{prompt}|--permission-mode|bypassPermissions";
            default -> "";
        };
    }

    private String defaultSendPromptToStdin(String profileName) {
        return "codex-local".equals(profileName) ? "true" : "false";
    }

    private String defaultCredentialTypes(String profileName) {
        return switch (profileName) {
            case "codex-local" -> "codex_cli_token,codex_api_key";
            case "claude-code-local" -> "claude_cli_token,anthropic_api_key";
            default -> "";
        };
    }

    private List<String> splitPipe(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|", -1))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(this::normalizeKey)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private Duration duration(String value) {
        try {
            Duration duration = Duration.parse(value);
            if (duration.isZero() || duration.isNegative()) {
                return Duration.ofMinutes(30);
            }
            return duration;
        } catch (RuntimeException ex) {
            return Duration.ofMinutes(30);
        }
    }

    private int positiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String firstText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    record CommandProfile(
            String name,
            String providerType,
            String command,
            List<String> arguments,
            String workingDirectory,
            boolean sendPromptToStdin,
            List<String> credentialTypes,
            Duration timeout
    ) {
    }
}
