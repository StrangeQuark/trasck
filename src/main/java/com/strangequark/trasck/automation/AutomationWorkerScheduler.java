package com.strangequark.trasck.automation;

import com.strangequark.trasck.agent.AgentService;
import com.strangequark.trasck.integration.EmailDeliveryWorkerRequest;
import com.strangequark.trasck.integration.ImportJobService;
import com.strangequark.trasck.integration.WebhookDeliveryWorkerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationWorkerScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomationWorkerScheduler.class);

    private final AutomationWorkerSettingsRepository automationWorkerSettingsRepository;
    private final AutomationService automationService;
    private final ImportJobService importJobService;
    private final AgentService agentService;

    public AutomationWorkerScheduler(
            AutomationWorkerSettingsRepository automationWorkerSettingsRepository,
            AutomationService automationService,
            ImportJobService importJobService,
            AgentService agentService
    ) {
        this.automationWorkerSettingsRepository = automationWorkerSettingsRepository;
        this.automationService = automationService;
        this.importJobService = importJobService;
        this.agentService = agentService;
    }

    @Scheduled(fixedDelayString = "${trasck.automation.workers.fixed-delay-ms:30000}")
    public void runEnabledWorkspaceWorkers() {
        for (AutomationWorkerSettings settings : automationWorkerSettingsRepository.findEnabledSettings()) {
            runWorkspaceWorkers(settings);
        }
    }

    @Scheduled(fixedDelayString = "${trasck.automation.worker-run-pruning.fixed-delay-ms:60000}")
    public void runAutomaticWorkerRunPruning() {
        for (AutomationWorkerSettings settings : automationWorkerSettingsRepository.findAutomaticPruningSettings()) {
            try {
                automationService.pruneWorkerRunsAutomatically(settings.getWorkspaceId());
            } catch (RuntimeException ex) {
                LOGGER.warn("Scheduled automation worker run pruning failed for workspace {}", settings.getWorkspaceId(), ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${trasck.agent.dispatch-attempt-pruning.fixed-delay-ms:60000}")
    public void runAutomaticAgentDispatchAttemptPruning() {
        for (AutomationWorkerSettings settings : automationWorkerSettingsRepository.findAutomaticAgentDispatchAttemptPruningSettings()) {
            try {
                agentService.pruneDispatchAttemptsAutomatically(settings.getWorkspaceId());
            } catch (RuntimeException ex) {
                LOGGER.warn("Scheduled agent dispatch attempt pruning failed for workspace {}", settings.getWorkspaceId(), ex);
            }
        }
    }

    private void runWorkspaceWorkers(AutomationWorkerSettings settings) {
        if (Boolean.TRUE.equals(settings.getAutomationJobsEnabled())) {
            runWorkerStep(settings, "automation", () -> {
                automationService.runQueuedJobsInternal(settings.getWorkspaceId(), settings.getAutomationLimit(), null);
            });
        }
        if (Boolean.TRUE.equals(settings.getWebhookDeliveriesEnabled())) {
            runWorkerStep(settings, "webhook", () -> {
                automationService.processWebhookDeliveriesInternal(
                        settings.getWorkspaceId(),
                        new WebhookDeliveryWorkerRequest(
                                settings.getWebhookLimit(),
                                settings.getWebhookMaxAttempts(),
                                settings.getWebhookDryRun()
                        ),
                        null
                );
            });
        }
        if (Boolean.TRUE.equals(settings.getEmailDeliveriesEnabled())) {
            runWorkerStep(settings, "email", () -> {
                automationService.processEmailDeliveriesInternal(
                        settings.getWorkspaceId(),
                        new EmailDeliveryWorkerRequest(
                                settings.getEmailLimit(),
                                settings.getEmailMaxAttempts(),
                                settings.getEmailDryRun()
                        ),
                        null
                );
            });
        }
        if (Boolean.TRUE.equals(settings.getImportConflictResolutionEnabled())) {
            runWorkerStep(settings, "import_conflict_resolution", () -> {
                importJobService.processConflictResolutionJobsInternal(
                        settings.getWorkspaceId(),
                        settings.getImportConflictResolutionLimit(),
                        null
                );
            });
        }
        if (Boolean.TRUE.equals(settings.getImportReviewExportsEnabled())) {
            runWorkerStep(settings, "import_review_export", () -> {
                importJobService.processImportReviewCsvExportJobsInternal(
                        settings.getWorkspaceId(),
                        settings.getImportReviewExportLimit(),
                        null
                );
            });
        }
    }

    private void runWorkerStep(AutomationWorkerSettings settings, String workerType, Runnable worker) {
        try {
            worker.run();
        } catch (RuntimeException ex) {
            LOGGER.warn("Scheduled {} worker failed for workspace {}", workerType, settings.getWorkspaceId(), ex);
        }
    }
}
