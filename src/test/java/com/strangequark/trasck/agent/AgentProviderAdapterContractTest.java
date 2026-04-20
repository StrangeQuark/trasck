package com.strangequark.trasck.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentProviderAdapterContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void adaptersReturnStableDispatchRetryAndCancelPayloadsWithoutExternalCalls() {
        List<AgentProviderAdapter> adapters = List.of(
                new SimulatedAgentProviderAdapter(objectMapper),
                new CodexAgentProviderAdapter(objectMapper),
                new ClaudeCodeAgentProviderAdapter(objectMapper),
                new GenericWorkerAgentProviderAdapter(objectMapper)
        );

        for (AgentProviderAdapter adapter : adapters) {
            AgentProvider provider = provider(adapter.providerType());
            AgentProfile profile = profile(provider);
            AgentTask task = task(provider, profile);

            adapter.validateProvider(provider);
            AgentDispatchResult dispatch = adapter.dispatch(task, provider, profile);
            AgentDispatchResult retry = adapter.retry(task, provider, profile);
            adapter.cancel(task, provider, profile);

            assertThat(dispatch.externalTaskId()).isNotBlank();
            assertThat(dispatch.dispatchPayload().path("adapter").asText()).isEqualTo(adapter.providerType());
            assertThat(dispatch.dispatchPayload().path("action").asText()).isEqualTo("dispatched");
            assertThat(dispatch.dispatchPayload().path("agentTaskId").asText()).isEqualTo(task.getId().toString());
            assertThat(dispatch.dispatchPayload().path("requiresCallbackJwt").asBoolean()).isTrue();
            assertThat(dispatch.dispatchPayload().path("idempotencyKey").asText()).contains(task.getId().toString());
            assertThat(dispatch.dispatchPayload().path("retrySupported").asBoolean()).isTrue();
            assertThat(dispatch.dispatchPayload().path("cancelSupported").asBoolean()).isTrue();
            assertThat(dispatch.dispatchPayload().path("requestChangesSupported").asBoolean()).isTrue();
            assertThat(dispatch.dispatchPayload().path("artifactCallbackSupported").asBoolean()).isTrue();
            assertThat(retry.externalTaskId()).isNotBlank();
            assertThat(retry.dispatchPayload().path("action").asText()).isEqualTo("retried");
            assertThat(retry.dispatchPayload().path("agentTaskId").asText()).isEqualTo(task.getId().toString());
            if ("generic_worker".equals(adapter.providerType())) {
                assertThat(dispatch.dispatchPayload().path("protocolVersion").asText()).isEqualTo("trasck.worker.v1");
                assertThat(dispatch.dispatchPayload().path("webhookPushSupported").asBoolean()).isTrue();
                assertThat(dispatch.dispatchPayload().path("pollingSupported").asBoolean()).isTrue();
            } else {
                assertThat(dispatch.dispatchPayload().path("protocolVersion").asText()).isEqualTo("trasck.agent-runtime.v1");
            }
        }
    }

    @Test
    void codexAndClaudeAdaptersExposeHostedApiAndCliWorkerBoundariesWithoutInvokingThem() {
        List<AgentProviderAdapter> adapters = List.of(
                new CodexAgentProviderAdapter(objectMapper),
                new ClaudeCodeAgentProviderAdapter(objectMapper)
        );

        for (AgentProviderAdapter adapter : adapters) {
            AgentProvider hosted = provider(adapter.providerType());
            ObjectNode hostedRuntime = objectMapper.createObjectNode()
                    .put("mode", "hosted_api")
                    .put("externalExecutionEnabled", true);
            hostedRuntime.set("hostedApi", objectMapper.createObjectNode()
                    .put("baseUrl", "https://agents.example.test")
                    .put("dispatchPath", "/dispatch"));
            hosted.setConfig(objectMapper.createObjectNode().set("runtime", hostedRuntime));
            AgentProfile hostedProfile = profile(hosted);
            AgentTask hostedTask = task(hosted, hostedProfile);

            adapter.validateProvider(hosted);
            AgentDispatchResult hostedDispatch = adapter.dispatch(hostedTask, hosted, hostedProfile);
            assertThat(hostedDispatch.dispatchPayload().path("providerRuntime").asText()).isEqualTo("hosted_api");
            assertThat(hostedDispatch.dispatchPayload().path("transport").asText()).isEqualTo("provider_hosted_api");
            assertThat(hostedDispatch.dispatchPayload().path("externalDispatch").asBoolean()).isTrue();
            assertThat(hostedDispatch.dispatchPayload().path("hostedApi").path("baseUrl").asText()).isEqualTo("https://agents.example.test");

            AgentProvider cli = provider(adapter.providerType());
            ObjectNode cliRuntime = objectMapper.createObjectNode()
                    .put("mode", "cli_worker")
                    .put("externalExecutionEnabled", true);
            cliRuntime.set("cliWorker", objectMapper.createObjectNode()
                    .put("commandProfile", adapter.providerType() + "-default")
                    .put("queueName", "local-agent"));
            cli.setConfig(objectMapper.createObjectNode().set("runtime", cliRuntime));
            AgentProfile cliProfile = profile(cli);
            AgentTask cliTask = task(cli, cliProfile);

            adapter.validateProvider(cli);
            AgentDispatchResult cliDispatch = adapter.dispatch(cliTask, cli, cliProfile);
            assertThat(cliDispatch.dispatchPayload().path("providerRuntime").asText()).isEqualTo("cli_worker");
            assertThat(cliDispatch.dispatchPayload().path("transport").asText()).isEqualTo("backend_cli_worker");
            assertThat(cliDispatch.dispatchPayload().path("externalDispatch").asBoolean()).isTrue();
            assertThat(cliDispatch.dispatchPayload().path("cliWorker").path("commandProfile").asText()).contains("default");
        }
    }

    @Test
    void realRuntimeModesRequireExplicitEnablementAndAllowlistedCliProfiles() {
        AgentProvider hostedWithoutOptIn = provider("codex");
        hostedWithoutOptIn.setConfig(objectMapper.createObjectNode().set("runtime", objectMapper.createObjectNode()
                .put("mode", "hosted_api")
                .set("hostedApi", objectMapper.createObjectNode().put("baseUrl", "https://agents.example.test"))));
        assertThatThrownBy(() -> new CodexAgentProviderAdapter(objectMapper).validateProvider(hostedWithoutOptIn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicitly enabled");

        AgentProvider rawCommand = provider("claude_code");
        ObjectNode cliRuntime = objectMapper.createObjectNode()
                .put("mode", "cli_worker")
                .put("externalExecutionEnabled", true);
        cliRuntime.set("cliWorker", objectMapper.createObjectNode()
                .put("commandProfile", "claude-default")
                .put("command", "claude --dangerously-run-this"));
        rawCommand.setConfig(objectMapper.createObjectNode().set("runtime", cliRuntime));
        assertThatThrownBy(() -> new ClaudeCodeAgentProviderAdapter(objectMapper).validateProvider(rawCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowlisted commandProfile");
    }

    @Test
    void adaptersRejectMismatchedProviderTypes() {
        AgentProvider mismatched = provider("simulated");
        assertThatThrownBy(() -> new CodexAgentProviderAdapter(objectMapper).validateProvider(mismatched))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AgentProvider provider(String providerType) {
        AgentProvider provider = new AgentProvider();
        provider.setId(UUID.randomUUID());
        provider.setWorkspaceId(UUID.randomUUID());
        provider.setProviderKey(providerType + "-provider");
        provider.setProviderType(providerType);
        provider.setDisplayName(providerType + " Provider");
        provider.setDispatchMode("generic_worker".equals(providerType) ? "webhook_push" : "managed");
        provider.setCallbackUrl("generic_worker".equals(providerType) ? "https://worker.example.test/dispatch" : null);
        provider.setCapabilitySchema(objectMapper.createObjectNode());
        provider.setConfig(objectMapper.createObjectNode());
        provider.setEnabled(true);
        return provider;
    }

    private AgentProfile profile(AgentProvider provider) {
        AgentProfile profile = new AgentProfile();
        profile.setId(UUID.randomUUID());
        profile.setWorkspaceId(provider.getWorkspaceId());
        profile.setProviderId(provider.getId());
        profile.setUserId(UUID.randomUUID());
        profile.setDisplayName("Agent");
        profile.setStatus("active");
        profile.setMaxConcurrentTasks(1);
        profile.setCapabilities(objectMapper.createObjectNode());
        profile.setConfig(objectMapper.createObjectNode());
        return profile;
    }

    private AgentTask task(AgentProvider provider, AgentProfile profile) {
        AgentTask task = new AgentTask();
        task.setId(UUID.randomUUID());
        task.setWorkspaceId(provider.getWorkspaceId());
        task.setProviderId(provider.getId());
        task.setAgentProfileId(profile.getId());
        task.setWorkItemId(UUID.randomUUID());
        task.setRequestedById(UUID.randomUUID());
        task.setStatus("queued");
        task.setDispatchMode(provider.getDispatchMode());
        ObjectNode context = objectMapper.createObjectNode()
                .put("workItemKey", "TEST-1")
                .put("title", "Adapter contract");
        task.setContextSnapshot(context);
        task.setRequestPayload(objectMapper.createObjectNode().put("instructions", "Exercise adapter contract"));
        task.setQueuedAt(OffsetDateTime.now());
        return task;
    }
}
