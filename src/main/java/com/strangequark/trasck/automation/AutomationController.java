package com.strangequark.trasck.automation;

import com.strangequark.trasck.integration.WebhookDeliveryResponse;
import com.strangequark.trasck.integration.WebhookRequest;
import com.strangequark.trasck.integration.WebhookResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AutomationController {

    private final AutomationService automationService;

    public AutomationController(AutomationService automationService) {
        this.automationService = automationService;
    }

    @GetMapping("/workspaces/{workspaceId}/automation-rules")
    public List<AutomationRuleResponse> listRules(@PathVariable UUID workspaceId) {
        return automationService.listRules(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/automation-rules")
    public ResponseEntity<AutomationRuleResponse> createRule(
            @PathVariable UUID workspaceId,
            @RequestBody AutomationRuleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(automationService.createRule(workspaceId, request));
    }

    @GetMapping("/automation-rules/{ruleId}")
    public AutomationRuleResponse getRule(@PathVariable UUID ruleId) {
        return automationService.getRule(ruleId);
    }

    @PatchMapping("/automation-rules/{ruleId}")
    public AutomationRuleResponse updateRule(@PathVariable UUID ruleId, @RequestBody AutomationRuleRequest request) {
        return automationService.updateRule(ruleId, request);
    }

    @DeleteMapping("/automation-rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID ruleId) {
        automationService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/automation-rules/{ruleId}/conditions")
    public ResponseEntity<AutomationConditionResponse> createCondition(
            @PathVariable UUID ruleId,
            @RequestBody AutomationConditionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(automationService.createCondition(ruleId, request));
    }

    @PatchMapping("/automation-rules/{ruleId}/conditions/{conditionId}")
    public AutomationConditionResponse updateCondition(
            @PathVariable UUID ruleId,
            @PathVariable UUID conditionId,
            @RequestBody AutomationConditionRequest request
    ) {
        return automationService.updateCondition(ruleId, conditionId, request);
    }

    @DeleteMapping("/automation-rules/{ruleId}/conditions/{conditionId}")
    public ResponseEntity<Void> deleteCondition(@PathVariable UUID ruleId, @PathVariable UUID conditionId) {
        automationService.deleteCondition(ruleId, conditionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/automation-rules/{ruleId}/actions")
    public ResponseEntity<AutomationActionResponse> createAction(
            @PathVariable UUID ruleId,
            @RequestBody AutomationActionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(automationService.createAction(ruleId, request));
    }

    @PatchMapping("/automation-rules/{ruleId}/actions/{actionId}")
    public AutomationActionResponse updateAction(
            @PathVariable UUID ruleId,
            @PathVariable UUID actionId,
            @RequestBody AutomationActionRequest request
    ) {
        return automationService.updateAction(ruleId, actionId, request);
    }

    @DeleteMapping("/automation-rules/{ruleId}/actions/{actionId}")
    public ResponseEntity<Void> deleteAction(@PathVariable UUID ruleId, @PathVariable UUID actionId) {
        automationService.deleteAction(ruleId, actionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/automation-rules/{ruleId}/execute")
    public AutomationExecutionJobResponse executeRule(
            @PathVariable UUID ruleId,
            @RequestBody(required = false) AutomationExecutionRequest request
    ) {
        return automationService.executeRule(ruleId, request);
    }

    @GetMapping("/automation-rules/{ruleId}/jobs")
    public List<AutomationExecutionJobResponse> listJobs(@PathVariable UUID ruleId) {
        return automationService.listJobs(ruleId);
    }

    @GetMapping("/automation-jobs/{jobId}")
    public AutomationExecutionJobResponse getJob(@PathVariable UUID jobId) {
        return automationService.getJob(jobId);
    }

    @GetMapping("/workspaces/{workspaceId}/webhooks")
    public List<WebhookResponse> listWebhooks(@PathVariable UUID workspaceId) {
        return automationService.listWebhooks(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/webhooks")
    public ResponseEntity<WebhookResponse> createWebhook(@PathVariable UUID workspaceId, @RequestBody WebhookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(automationService.createWebhook(workspaceId, request));
    }

    @PatchMapping("/webhooks/{webhookId}")
    public WebhookResponse updateWebhook(@PathVariable UUID webhookId, @RequestBody WebhookRequest request) {
        return automationService.updateWebhook(webhookId, request);
    }

    @DeleteMapping("/webhooks/{webhookId}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable UUID webhookId) {
        automationService.deleteWebhook(webhookId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/webhooks/{webhookId}/deliveries")
    public List<WebhookDeliveryResponse> listWebhookDeliveries(@PathVariable UUID webhookId) {
        return automationService.listWebhookDeliveries(webhookId);
    }
}
