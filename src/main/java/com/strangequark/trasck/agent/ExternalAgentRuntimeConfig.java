package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ExternalAgentRuntimeConfig {

    private static final List<String> SUPPORTED_MODES = List.of("stub", "hosted_api", "cli_worker");

    private final ObjectMapper objectMapper;
    private final String adapterType;
    private final String mode;
    private final boolean externalExecutionEnabled;
    private final JsonNode hostedApi;
    private final JsonNode cliWorker;

    private ExternalAgentRuntimeConfig(
            ObjectMapper objectMapper,
            String adapterType,
            String mode,
            boolean externalExecutionEnabled,
            JsonNode hostedApi,
            JsonNode cliWorker
    ) {
        this.objectMapper = objectMapper;
        this.adapterType = adapterType;
        this.mode = mode;
        this.externalExecutionEnabled = externalExecutionEnabled;
        this.hostedApi = hostedApi;
        this.cliWorker = cliWorker;
    }

    static ExternalAgentRuntimeConfig from(ObjectMapper objectMapper, String adapterType, AgentProvider provider) {
        JsonNode config = provider.getConfig() == null ? objectMapper.createObjectNode() : provider.getConfig();
        JsonNode runtime = firstObject(config.path("runtime"), config.path("agentRuntime"));
        String mode = normalize(firstText(runtime.path("mode"), config.path("runtimeMode"), "stub"));
        if (!SUPPORTED_MODES.contains(mode)) {
            throw new IllegalArgumentException("Agent provider runtime mode must be stub, hosted_api, or cli_worker");
        }
        boolean externalExecutionEnabled = runtime.path("externalExecutionEnabled").asBoolean(config.path("externalExecutionEnabled").asBoolean(false));
        JsonNode hostedApi = firstObject(runtime.path("hostedApi"), config.path("hostedApi"));
        JsonNode cliWorker = firstObject(runtime.path("cliWorker"), config.path("cliWorker"));
        return new ExternalAgentRuntimeConfig(objectMapper, adapterType, mode, externalExecutionEnabled, hostedApi, cliWorker);
    }

    void validate() {
        if ("stub".equals(mode)) {
            return;
        }
        if (!externalExecutionEnabled) {
            throw new IllegalArgumentException("External agent execution must be explicitly enabled for hosted_api or cli_worker runtime modes");
        }
        if ("hosted_api".equals(mode)) {
            if (!hasText(text(hostedApi, "baseUrl"))) {
                throw new IllegalArgumentException("hosted_api runtime requires runtime.hostedApi.baseUrl");
            }
            return;
        }
        if (!hasText(text(cliWorker, "commandProfile"))) {
            throw new IllegalArgumentException("cli_worker runtime requires runtime.cliWorker.commandProfile");
        }
        if (hasText(text(cliWorker, "command")) || cliWorker.path("args").isArray()) {
            throw new IllegalArgumentException("cli_worker runtime must use an allowlisted commandProfile instead of raw commands");
        }
    }

    ObjectNode dispatchPayload(AgentTask task, AgentProvider provider, AgentProfile profile, String action) {
        return runtimePayload(task.getId(), profile.getId(), provider, action);
    }

    ObjectNode previewPayload(UUID taskId, UUID profileId, AgentProvider provider, String action) {
        return runtimePayload(taskId, profileId, provider, action);
    }

    String externalTaskId(AgentTask task) {
        return externalTaskId(task.getId());
    }

    String externalTaskId(UUID taskId) {
        return adapterType.replace("_", "-") + "-" + mode.replace("_", "-") + "-" + taskId;
    }

    String mode() {
        return mode;
    }

    boolean externalExecutionEnabled() {
        return externalExecutionEnabled;
    }

    String transport() {
        return switch (mode) {
            case "hosted_api" -> "provider_hosted_api";
            case "cli_worker" -> "backend_cli_worker";
            default -> "internal_stub";
        };
    }

    private ObjectNode runtimePayload(UUID taskId, UUID profileId, AgentProvider provider, String action) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("adapter", adapterType)
                .put("protocolVersion", "trasck.agent-runtime.v1")
                .put("action", action)
                .put("providerRuntime", mode)
                .put("transport", transport())
                .put("externalDispatch", !"stub".equals(mode))
                .put("externalExecutionEnabled", externalExecutionEnabled)
                .put("agentTaskId", taskId.toString())
                .put("providerId", provider.getId().toString())
                .put("providerKey", provider.getProviderKey())
                .put("agentProfileId", profileId.toString())
                .put("callbackHeaderName", AgentCallbackJwtService.CALLBACK_HEADER)
                .put("requiresCallbackJwt", true)
                .put("idempotencyKey", idempotencyKey(taskId, action))
                .put("retrySupported", true)
                .put("cancelSupported", true)
                .put("requestChangesSupported", true)
                .put("artifactCallbackSupported", true);
        if ("hosted_api".equals(mode)) {
            payload.set("hostedApi", hostedApiPayload());
        } else if ("cli_worker".equals(mode)) {
            payload.set("cliWorker", cliWorkerPayload());
        }
        return payload;
    }

    private ObjectNode hostedApiPayload() {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("baseUrl", text(hostedApi, "baseUrl"));
        copyText(hostedApi, payload, "dispatchPath");
        copyText(hostedApi, payload, "retryPath");
        copyText(hostedApi, payload, "cancelPath");
        copyText(hostedApi, payload, "artifactPath");
        return payload;
    }

    private ObjectNode cliWorkerPayload() {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("commandProfile", text(cliWorker, "commandProfile"));
        copyText(cliWorker, payload, "queueName");
        copyText(cliWorker, payload, "workingDirectoryProfile");
        return payload;
    }

    private String idempotencyKey(UUID taskId, String action) {
        return adapterType + ":" + mode + ":" + taskId + ":" + action;
    }

    private static JsonNode firstObject(JsonNode preferred, JsonNode fallback) {
        if (preferred != null && preferred.isObject()) {
            return preferred;
        }
        if (fallback != null && fallback.isObject()) {
            return fallback;
        }
        return preferred == null ? fallback : preferred;
    }

    private static String firstText(JsonNode preferred, JsonNode fallback, String defaultValue) {
        if (preferred != null && preferred.isTextual() && hasText(preferred.asText())) {
            return preferred.asText();
        }
        if (fallback != null && fallback.isTextual() && hasText(fallback.asText())) {
            return fallback.asText();
        }
        return defaultValue;
    }

    private static String text(JsonNode node, String fieldName) {
        return node == null || !node.path(fieldName).isTextual() ? null : node.path(fieldName).asText();
    }

    private static void copyText(JsonNode source, ObjectNode target, String fieldName) {
        String value = text(source, fieldName);
        if (hasText(value)) {
            target.put(fieldName, value.trim());
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
