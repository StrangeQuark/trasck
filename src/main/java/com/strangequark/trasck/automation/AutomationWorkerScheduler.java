package com.strangequark.trasck.automation;

import com.strangequark.trasck.integration.EmailDeliveryWorkerRequest;
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

    public AutomationWorkerScheduler(
            AutomationWorkerSettingsRepository automationWorkerSettingsRepository,
            AutomationService automationService
    ) {
        this.automationWorkerSettingsRepository = automationWorkerSettingsRepository;
        this.automationService = automationService;
    }

    @Scheduled(fixedDelayString = "${trasck.automation.workers.fixed-delay-ms:30000}")
    public void runEnabledWorkspaceWorkers() {
        for (AutomationWorkerSettings settings : automationWorkerSettingsRepository.findEnabledSettings()) {
            runWorkspaceWorkers(settings);
        }
    }

    private void runWorkspaceWorkers(AutomationWorkerSettings settings) {
        try {
            if (Boolean.TRUE.equals(settings.getAutomationJobsEnabled())) {
                automationService.runQueuedJobsInternal(settings.getWorkspaceId(), settings.getAutomationLimit(), null);
            }
            if (Boolean.TRUE.equals(settings.getWebhookDeliveriesEnabled())) {
                automationService.processWebhookDeliveriesInternal(
                        settings.getWorkspaceId(),
                        new WebhookDeliveryWorkerRequest(
                                settings.getWebhookLimit(),
                                settings.getWebhookMaxAttempts(),
                                settings.getWebhookDryRun()
                        ),
                        null
                );
            }
            if (Boolean.TRUE.equals(settings.getEmailDeliveriesEnabled())) {
                automationService.processEmailDeliveriesInternal(
                        settings.getWorkspaceId(),
                        new EmailDeliveryWorkerRequest(
                                settings.getEmailLimit(),
                                settings.getEmailMaxAttempts(),
                                settings.getEmailDryRun()
                        ),
                        null
                );
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("Scheduled automation workers failed for workspace {}", settings.getWorkspaceId(), ex);
        }
    }
}
