package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.JsonValues;
import com.strangequark.trasck.security.SecretTextRedactor;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AgentCliWorkerDispatcher {

    private static final List<String> ACTIVE_CLI_RUN_STATUSES = List.of("queued", "running", "waiting_for_input");

    private final ObjectMapper objectMapper;
    private final AgentCliWorkerProperties properties;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentProviderRepository agentProviderRepository;
    private final AgentProfileRepository agentProfileRepository;
    private final AgentTaskRepositoryLinkRepository agentTaskRepositoryLinkRepository;
    private final AgentTaskEventRepository agentTaskEventRepository;
    private final AgentProviderCredentialRepository agentProviderCredentialRepository;
    private final SecretCipherService secretCipherService;
    private final SecretTextRedactor secretTextRedactor;
    private final ObjectProvider<AgentService> agentServiceProvider;
    private final ExecutorService executor;

    public AgentCliWorkerDispatcher(
            ObjectMapper objectMapper,
            AgentCliWorkerProperties properties,
            AgentTaskRepository agentTaskRepository,
            AgentProviderRepository agentProviderRepository,
            AgentProfileRepository agentProfileRepository,
            AgentTaskRepositoryLinkRepository agentTaskRepositoryLinkRepository,
            AgentTaskEventRepository agentTaskEventRepository,
            AgentProviderCredentialRepository agentProviderCredentialRepository,
            SecretCipherService secretCipherService,
            SecretTextRedactor secretTextRedactor,
            ObjectProvider<AgentService> agentServiceProvider
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.agentTaskRepository = agentTaskRepository;
        this.agentProviderRepository = agentProviderRepository;
        this.agentProfileRepository = agentProfileRepository;
        this.agentTaskRepositoryLinkRepository = agentTaskRepositoryLinkRepository;
        this.agentTaskEventRepository = agentTaskEventRepository;
        this.agentProviderCredentialRepository = agentProviderCredentialRepository;
        this.secretCipherService = secretCipherService;
        this.secretTextRedactor = secretTextRedactor;
        this.agentServiceProvider = agentServiceProvider;
        this.executor = Executors.newFixedThreadPool(properties.maxConcurrency(), threadFactory());
    }

    void dispatchAfterCommit(
            AgentTask task,
            AgentProvider provider,
            AgentProfile profile,
            JsonNode dispatchPayload,
            String callbackToken
    ) {
        if (!properties.enabled() || !shouldRunCliWorker(provider)) {
            return;
        }
        Runnable dispatch = () -> executor.execute(() -> runCliWorker(task.getId(), provider.getId(), profile.getId(), dispatchPayload, callbackToken));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    List<AgentCliWorkerRunResponse> listRuns(UUID workspaceId) {
        Path root = properties.workspaceRoot();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<AgentCliWorkerRunResponse> runs = new ArrayList<>();
        try (Stream<Path> directories = Files.list(root)) {
            directories
                    .filter(directory -> Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                    .forEach(directory -> runResponse(workspaceId, directory).ifPresent(runs::add));
        } catch (IOException ex) {
            throw new IllegalStateException("CLI worker run directories could not be listed", ex);
        }
        runs.sort(Comparator.comparing(AgentCliWorkerRunResponse::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return runs;
    }

    AgentCliWorkerRunArchive archiveRun(AgentTask task) {
        Path directory = requireExistingRunDirectory(task);
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.toList()) {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.normalize().startsWith(directory)) {
                        continue;
                    }
                    String entryName = directory.relativize(path).toString().replace('\\', '/');
                    if (entryName.isBlank()) {
                        continue;
                    }
                    ZipEntry entry = new ZipEntry(entryName);
                    entry.setTime(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis());
                    zip.putNextEntry(entry);
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
            zip.finish();
            byte[] bytes = buffer.toByteArray();
            return new AgentCliWorkerRunArchive("agent-cli-run-" + task.getId() + ".zip", bytes, bytes.length);
        } catch (IOException ex) {
            throw new IllegalStateException("CLI worker run archive could not be created", ex);
        }
    }

    AgentCliWorkerRunDeleteResponse deleteRun(AgentTask task) {
        Path directory = requireExistingRunDirectory(task);
        long deletedBytes = deleteDirectory(directory);
        return new AgentCliWorkerRunDeleteResponse(task.getWorkspaceId(), null, 1, deletedBytes, List.of(task.getId()));
    }

    AgentCliWorkerRunDeleteResponse pruneRuns(UUID workspaceId, OffsetDateTime cutoff) {
        List<UUID> deletedTaskIds = new ArrayList<>();
        long deletedBytes = 0;
        for (AgentCliWorkerRunResponse run : listRuns(workspaceId)) {
            if (run.updatedAt() == null || run.updatedAt().isAfter(cutoff) || ACTIVE_CLI_RUN_STATUSES.contains(run.status())) {
                continue;
            }
            Path directory = runDirectory(run.agentTaskId());
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            deletedBytes += deleteDirectory(directory);
            deletedTaskIds.add(run.agentTaskId());
        }
        return new AgentCliWorkerRunDeleteResponse(workspaceId, cutoff, deletedTaskIds.size(), deletedBytes, deletedTaskIds);
    }

    private boolean shouldRunCliWorker(AgentProvider provider) {
        if (!List.of("codex", "claude_code").contains(provider.getProviderType())) {
            return false;
        }
        ExternalAgentRuntimeConfig runtime = ExternalAgentRuntimeConfig.from(objectMapper, provider.getProviderType(), provider);
        return runtime.cliWorker() && runtime.externalExecutionEnabled();
    }

    private void runCliWorker(UUID taskId, UUID providerId, UUID profileId, JsonNode dispatchPayload, String callbackToken) {
        AgentTask task = null;
        AgentProvider provider = null;
        AgentProfile profile = null;
        Path runDirectory = null;
        try {
            task = agentTaskRepository.findById(taskId).orElseThrow(() -> new IllegalStateException("Agent task not found"));
            provider = agentProviderRepository.findById(providerId).orElseThrow(() -> new IllegalStateException("Agent provider not found"));
            profile = agentProfileRepository.findById(profileId).orElseThrow(() -> new IllegalStateException("Agent profile not found"));
            ExternalAgentRuntimeConfig runtime = ExternalAgentRuntimeConfig.from(objectMapper, provider.getProviderType(), provider);
            AgentCliWorkerProperties.CommandProfile commandProfile = properties.profile(provider.getProviderType(), runtime.commandProfile())
                    .orElseThrow(() -> new IllegalStateException("No allowlisted CLI command profile is configured for " + runtime.commandProfile()));
            if (!provider.getProviderType().equals(commandProfile.providerType())) {
                throw new IllegalStateException("CLI command profile " + commandProfile.name() + " cannot run provider type " + provider.getProviderType());
            }
            runDirectory = createRunDirectory(task);
            JsonNode taskFilePayload = taskFilePayload(task, provider, profile, dispatchPayload, runDirectory);
            Path promptFile = runDirectory.resolve("prompt.md");
            Path taskFile = runDirectory.resolve("task.json");
            String prompt = prompt(task, provider, profile, commandProfile, runDirectory);
            writeRunInputs(promptFile, prompt, taskFile, taskFilePayload);
            appendTaskEvent(task, "cli_worker_started", "info", "Backend CLI worker started.", objectMapper.createObjectNode()
                    .put("commandProfile", commandProfile.name())
                    .put("providerType", provider.getProviderType()));

            CredentialEnvironment credentialEnvironment = credentialEnvironment(provider, commandProfile).orElse(null);
            ProcessResult result = runProcess(commandProfile, prompt, promptFile, taskFile, runDirectory, task, provider, credentialEnvironment);
            handleProcessResult(task, provider, callbackToken, commandProfile, result);
        } catch (RuntimeException ex) {
            handleStartupFailure(task, provider, profile, callbackToken, runDirectory, ex);
        }
    }

    private void writeRunInputs(Path promptFile, String prompt, Path taskFile, JsonNode taskFilePayload) {
        try {
            Files.writeString(promptFile, prompt, StandardCharsets.UTF_8);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(taskFile.toFile(), taskFilePayload);
        } catch (IOException ex) {
            throw new IllegalStateException("CLI worker run input files could not be written", ex);
        }
    }

    private ProcessResult runProcess(
            AgentCliWorkerProperties.CommandProfile commandProfile,
            String prompt,
            Path promptFile,
            Path taskFile,
            Path runDirectory,
            AgentTask task,
            AgentProvider provider,
            CredentialEnvironment credentialEnvironment
    ) {
        Path outputFile = runDirectory.resolve("output.log");
        Path workingDirectory = workingDirectory(commandProfile, runDirectory);
        List<String> command = new ArrayList<>();
        command.add(commandProfile.command());
        for (String argument : commandProfile.arguments()) {
            command.add(applyPlaceholders(argument, prompt, promptFile, taskFile, runDirectory, task, provider));
        }
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile());
        builder.environment().put("TRASCK_AGENT_TASK_ID", task.getId().toString());
        builder.environment().put("TRASCK_AGENT_WORKSPACE_ID", task.getWorkspaceId().toString());
        builder.environment().put("TRASCK_AGENT_PROVIDER_KEY", provider.getProviderKey());
        builder.environment().put("TRASCK_AGENT_PROMPT_FILE", promptFile.toString());
        builder.environment().put("TRASCK_AGENT_TASK_FILE", taskFile.toString());
        if (credentialEnvironment != null) {
            builder.environment().put(credentialEnvironment.environmentVariable(), credentialEnvironment.secret());
        }
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            Process process = builder.start();
            try (OutputStream stdin = process.getOutputStream()) {
                if (commandProfile.sendPromptToStdin()) {
                    stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
                }
            }
            boolean finished = process.waitFor(commandProfile.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ProcessResult.timedOut(startedAt, OffsetDateTime.now(ZoneOffset.UTC), output(outputFile, credentialEnvironment), outputFile);
            }
            return new ProcessResult(
                    process.exitValue(),
                    false,
                    output(outputFile, credentialEnvironment),
                    outputFile,
                    startedAt,
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
        } catch (IOException ex) {
            throw new IllegalStateException("CLI worker process could not start", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CLI worker process was interrupted", ex);
        }
    }

    private void handleProcessResult(
            AgentTask task,
            AgentProvider provider,
            String callbackToken,
            AgentCliWorkerProperties.CommandProfile commandProfile,
            ProcessResult result
    ) {
        ObjectNode resultPayload = objectMapper.createObjectNode()
                .put("providerType", provider.getProviderType())
                .put("commandProfile", commandProfile.name())
                .put("exitCode", result.exitCode())
                .put("timedOut", result.timedOut())
                .put("startedAt", result.startedAt().toString())
                .put("finishedAt", result.finishedAt().toString())
                .put("outputTruncated", result.output().truncated());
        resultPayload.put("output", result.output().text());
        String agentName = "codex".equals(provider.getProviderType()) ? "Codex" : "Claude Code";
        if (result.exitCode() == 0 && !result.timedOut()) {
            appendTaskEvent(task, "cli_worker_completed", "info", agentName + " CLI worker finished.", objectMapper.createObjectNode()
                    .put("commandProfile", commandProfile.name())
                    .put("exitCode", result.exitCode()));
            agentServiceProvider.getObject().handleInternalAgentCallback(task.getId(), callbackToken, new AgentTaskCallbackRequest(
                    "completed",
                    agentName + " CLI worker completed and requested review.",
                    JsonValues.toJavaValue(resultPayload),
                    List.of(new AgentTaskCallbackArtifactRequest("cli_output", agentName + " CLI output", null, JsonValues.toJavaValue(resultPayload))),
                    List.of(new AgentTaskCallbackMessageRequest("agent", messageOutput(result.output().text(), agentName), null))
            ));
            return;
        }
        appendTaskEvent(task, "cli_worker_failed", "error", agentName + " CLI worker failed.", objectMapper.createObjectNode()
                .put("commandProfile", commandProfile.name())
                .put("exitCode", result.exitCode())
                .put("timedOut", result.timedOut()));
        agentServiceProvider.getObject().handleInternalAgentCallback(task.getId(), callbackToken, new AgentTaskCallbackRequest(
                "failed",
                result.timedOut() ? agentName + " CLI worker timed out." : agentName + " CLI worker exited with code " + result.exitCode() + ".",
                JsonValues.toJavaValue(resultPayload),
                List.of(),
                List.of()
        ));
    }

    private void handleStartupFailure(AgentTask task, AgentProvider provider, AgentProfile profile, String callbackToken, Path runDirectory, RuntimeException ex) {
        if (task == null || provider == null || profile == null || callbackToken == null) {
            return;
        }
        String agentName = "codex".equals(provider.getProviderType()) ? "Codex" : "Claude Code";
        String message = secretTextRedactor.redact(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        appendTaskEvent(task, "cli_worker_failed", "error", "Backend CLI worker failed before process execution.", objectMapper.createObjectNode()
                .put("errorMessage", message)
                .put("runDirectoryCreated", runDirectory != null));
        ObjectNode resultPayload = objectMapper.createObjectNode()
                .put("providerType", provider.getProviderType())
                .put("errorMessage", message);
        agentServiceProvider.getObject().handleInternalAgentCallback(task.getId(), callbackToken, new AgentTaskCallbackRequest(
                "failed",
                agentName + " CLI worker could not start.",
                JsonValues.toJavaValue(resultPayload),
                List.of(),
                List.of()
        ));
    }

    private Path workingDirectory(AgentCliWorkerProperties.CommandProfile commandProfile, Path runDirectory) {
        if (commandProfile.workingDirectory() == null || commandProfile.workingDirectory().isBlank()) {
            return runDirectory;
        }
        Path path = Path.of(commandProfile.workingDirectory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("CLI worker working directory does not exist");
        }
        return path;
    }

    private Path createRunDirectory(AgentTask task) {
        try {
            Path root = properties.workspaceRoot();
            Files.createDirectories(root);
            Path directory = runDirectory(task.getId());
            Files.createDirectories(directory);
            return directory;
        } catch (IOException ex) {
            throw new IllegalStateException("CLI worker run directory could not be created", ex);
        }
    }

    private Optional<AgentCliWorkerRunResponse> runResponse(UUID workspaceId, Path directory) {
        UUID taskId;
        try {
            taskId = UUID.fromString(directory.getFileName().toString());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        return agentTaskRepository.findByIdAndWorkspaceId(taskId, workspaceId)
                .map(task -> {
                    RunFileStats stats = directoryStats(directory);
                    String providerType = agentProviderRepository.findByIdAndWorkspaceId(task.getProviderId(), workspaceId)
                            .map(AgentProvider::getProviderType)
                            .orElse(null);
                    return new AgentCliWorkerRunResponse(
                            task.getId(),
                            task.getWorkspaceId(),
                            task.getProviderId(),
                            providerType,
                            task.getAgentProfileId(),
                            task.getWorkItemId(),
                            task.getStatus(),
                            task.getQueuedAt(),
                            directoryUpdatedAt(directory),
                            stats.sizeBytes(),
                            stats.fileCount(),
                            Files.exists(directory.resolve("prompt.md"), LinkOption.NOFOLLOW_LINKS),
                            Files.exists(directory.resolve("task.json"), LinkOption.NOFOLLOW_LINKS),
                            Files.exists(directory.resolve("output.log"), LinkOption.NOFOLLOW_LINKS)
                    );
                });
    }

    private Path requireExistingRunDirectory(AgentTask task) {
        Path directory = runDirectory(task.getId());
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("CLI worker run not found");
        }
        return directory;
    }

    private Path runDirectory(UUID taskId) {
        Path root = properties.workspaceRoot();
        Path directory = root.resolve(taskId.toString()).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalStateException("CLI worker run directory escaped workspace root");
        }
        return directory;
    }

    private RunFileStats directoryStats(Path directory) {
        long sizeBytes = 0;
        int fileCount = 0;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.toList()) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.normalize().startsWith(directory)) {
                    continue;
                }
                sizeBytes += Files.size(path);
                fileCount++;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("CLI worker run directory could not be inspected", ex);
        }
        return new RunFileStats(sizeBytes, fileCount);
    }

    private OffsetDateTime directoryUpdatedAt(Path directory) {
        try {
            return Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS).toInstant().atOffset(ZoneOffset.UTC);
        } catch (IOException ex) {
            throw new IllegalStateException("CLI worker run directory timestamp could not be read", ex);
        }
    }

    private long deleteDirectory(Path directory) {
        long deletedBytes = directoryStats(directory).sizeBytes();
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> sortedPaths = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : sortedPaths) {
                if (!path.normalize().startsWith(directory)) {
                    continue;
                }
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("CLI worker run directory could not be deleted", ex);
        }
        return deletedBytes;
    }

    private JsonNode taskFilePayload(AgentTask task, AgentProvider provider, AgentProfile profile, JsonNode dispatchPayload, Path runDirectory) {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("protocolVersion", "trasck.agent-cli-worker.v1")
                .put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString())
                .put("workspaceId", task.getWorkspaceId().toString())
                .put("workItemId", task.getWorkItemId().toString())
                .put("agentTaskId", task.getId().toString())
                .put("agentProfileId", profile.getId().toString())
                .put("providerId", provider.getId().toString())
                .put("providerKey", provider.getProviderKey())
                .put("providerType", provider.getProviderType())
                .put("runDirectory", runDirectory.toString());
        payload.set("contextSnapshot", task.getContextSnapshot() == null ? objectMapper.createObjectNode() : task.getContextSnapshot());
        payload.set("requestPayload", task.getRequestPayload() == null ? objectMapper.createObjectNode() : task.getRequestPayload());
        payload.set("dispatchPayload", dispatchPayload == null ? objectMapper.createObjectNode() : dispatchPayload);
        ArrayNode repositories = objectMapper.createArrayNode();
        for (AgentTaskRepositoryLink link : agentTaskRepositoryLinkRepository.findByAgentTaskIdOrderByCreatedAtAsc(task.getId())) {
            ObjectNode repository = objectMapper.createObjectNode()
                    .put("repositoryConnectionId", link.getRepositoryConnectionId().toString())
                    .put("baseBranch", link.getBaseBranch())
                    .put("workingBranch", link.getWorkingBranch());
            if (link.getPullRequestUrl() != null) {
                repository.put("pullRequestUrl", link.getPullRequestUrl());
            }
            repository.set("metadata", link.getMetadata() == null ? objectMapper.createObjectNode() : link.getMetadata());
            repositories.add(repository);
        }
        payload.set("repositories", repositories);
        return payload;
    }

    private String prompt(
            AgentTask task,
            AgentProvider provider,
            AgentProfile profile,
            AgentCliWorkerProperties.CommandProfile commandProfile,
            Path runDirectory
    ) {
        JsonNode context = task.getContextSnapshot() == null ? objectMapper.createObjectNode() : task.getContextSnapshot();
        JsonNode request = task.getRequestPayload() == null ? objectMapper.createObjectNode() : task.getRequestPayload();
        String agentName = "codex".equals(provider.getProviderType()) ? "Codex" : "Claude Code";
        StringBuilder builder = new StringBuilder();
        builder.append("# Trasck Agent Task\n\n");
        builder.append("You are running as the configured ").append(agentName).append(" agent for Trasck.\n");
        builder.append("Work only on the assigned story and preserve unrelated local changes. Do not print secrets or credentials.\n");
        builder.append("When finished, print a concise review summary with files changed, tests run, blockers, and next review notes. Trasck captures stdout and will request human review automatically.\n\n");
        builder.append("Provider key: ").append(provider.getProviderKey()).append('\n');
        builder.append("Command profile: ").append(commandProfile.name()).append('\n');
        builder.append("Agent profile: ").append(profile.getDisplayName()).append(" (").append(profile.getId()).append(")\n");
        builder.append("Task ID: ").append(task.getId()).append('\n');
        builder.append("Run directory: ").append(runDirectory).append("\n\n");
        builder.append("## Work Item\n");
        appendField(builder, "Key", context.path("workItemKey").asText(null));
        appendField(builder, "Title", context.path("title").asText(null));
        appendField(builder, "Description", context.path("descriptionMarkdown").asText(null));
        builder.append("\n## Instructions\n");
        builder.append(firstText(request.path("instructions").asText(null), "Review this work item and prepare implementation output.")).append("\n\n");
        builder.append("## Repository Hints\n");
        List<AgentTaskRepositoryLink> repositories = agentTaskRepositoryLinkRepository.findByAgentTaskIdOrderByCreatedAtAsc(task.getId());
        if (repositories.isEmpty()) {
            builder.append("- No repository connection was attached to this task.\n");
        } else {
            for (AgentTaskRepositoryLink repository : repositories) {
                builder.append("- Connection ").append(repository.getRepositoryConnectionId())
                        .append(", base branch ").append(firstText(repository.getBaseBranch(), "main"))
                        .append(", working branch ").append(firstText(repository.getWorkingBranch(), "trasck/agent-" + task.getId()));
                if (repository.getMetadata() != null && repository.getMetadata().hasNonNull("repositoryUrl")) {
                    builder.append(", URL ").append(repository.getMetadata().path("repositoryUrl").asText());
                }
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private void appendField(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append(": ").append(value).append('\n');
        }
    }

    private String applyPlaceholders(String value, String prompt, Path promptFile, Path taskFile, Path runDirectory, AgentTask task, AgentProvider provider) {
        return value
                .replace("{prompt}", prompt)
                .replace("{promptFile}", promptFile.toString())
                .replace("{taskFile}", taskFile.toString())
                .replace("{workspaceDir}", runDirectory.toString())
                .replace("{taskId}", task.getId().toString())
                .replace("{providerKey}", provider.getProviderKey());
    }

    private OutputText output(Path outputFile, CredentialEnvironment credentialEnvironment) {
        try {
            if (!Files.exists(outputFile)) {
                return new OutputText("", false);
            }
            BoundedBytes bytes = readBounded(outputFile, properties.maxOutputBytes());
            String text = new String(bytes.bytes(), StandardCharsets.UTF_8);
            text = secretTextRedactor.redact(text);
            if (credentialEnvironment != null && credentialEnvironment.secret() != null && !credentialEnvironment.secret().isBlank()) {
                text = text.replace(credentialEnvironment.secret(), "[redacted]");
            }
            return new OutputText(text, bytes.truncated());
        } catch (IOException ex) {
            return new OutputText("Could not read CLI worker output.", false);
        }
    }

    private BoundedBytes readBounded(Path outputFile, int maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(outputFile);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024))) {
            byte[] chunk = new byte[8192];
            int captured = 0;
            boolean truncated = false;
            int read;
            while ((read = input.read(chunk)) != -1) {
                int remaining = maxBytes - captured;
                if (remaining <= 0) {
                    truncated = true;
                    break;
                }
                int toWrite = Math.min(read, remaining);
                buffer.write(chunk, 0, toWrite);
                captured += toWrite;
                if (read > toWrite) {
                    truncated = true;
                    break;
                }
            }
            return new BoundedBytes(buffer.toByteArray(), truncated);
        }
    }

    private Optional<CredentialEnvironment> credentialEnvironment(AgentProvider provider, AgentCliWorkerProperties.CommandProfile commandProfile) {
        for (String credentialType : commandProfile.credentialTypes()) {
            for (AgentProviderCredential credential : agentProviderCredentialRepository.findByProviderIdAndCredentialTypeAndActiveTrue(provider.getId(), credentialType)) {
                if (credential.getExpiresAt() != null && !credential.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
                    continue;
                }
                String envName = credentialEnvironmentVariable(credential);
                if (envName == null) {
                    continue;
                }
                return Optional.of(new CredentialEnvironment(envName, secretCipherService.decrypt(credential.getEncryptedSecret())));
            }
        }
        return Optional.empty();
    }

    private String credentialEnvironmentVariable(AgentProviderCredential credential) {
        JsonNode metadata = credential.getMetadata();
        if (metadata != null) {
            if (metadata.hasNonNull("environmentVariable") && !metadata.path("environmentVariable").asText().isBlank()) {
                return metadata.path("environmentVariable").asText().trim();
            }
            if (metadata.hasNonNull("envName") && !metadata.path("envName").asText().isBlank()) {
                return metadata.path("envName").asText().trim();
            }
        }
        return switch (credential.getCredentialType()) {
            case "codex_api_key" -> "CODEX_API_KEY";
            case "anthropic_api_key" -> "ANTHROPIC_API_KEY";
            default -> null;
        };
    }

    private String messageOutput(String output, String agentName) {
        String text = firstText(output, agentName + " CLI worker finished without stdout.").trim();
        if (text.length() > 8_000) {
            return text.substring(0, 8_000) + "\n\n[output truncated]";
        }
        return text;
    }

    private void appendTaskEvent(AgentTask task, String eventType, String severity, String message, JsonNode metadata) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setAgentTaskId(task.getId());
        event.setEventType(eventType);
        event.setSeverity(severity);
        event.setMessage(message);
        event.setMetadata(metadata == null ? objectMapper.createObjectNode() : metadata);
        agentTaskEventRepository.save(event);
    }

    private String firstText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private ThreadFactory threadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "trasck-agent-cli-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record CredentialEnvironment(String environmentVariable, String secret) {
    }

    private record OutputText(String text, boolean truncated) {
    }

    private record BoundedBytes(byte[] bytes, boolean truncated) {
    }

    private record RunFileStats(long sizeBytes, int fileCount) {
    }

    private record ProcessResult(
            int exitCode,
            boolean timedOut,
            OutputText output,
            Path outputFile,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt
    ) {
        static ProcessResult timedOut(OffsetDateTime startedAt, OffsetDateTime finishedAt, OutputText output, Path outputFile) {
            return new ProcessResult(-1, true, output, outputFile, startedAt, finishedAt);
        }
    }
}
