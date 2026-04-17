package com.strangequark.trasck.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "agent_tasks")
public class AgentTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "work_item_id")
    private UUID workItemId;

    @Column(name = "agent_profile_id")
    private UUID agentProfileId;

    @Column(name = "provider_id")
    private UUID providerId;

    @Column(name = "requested_by_id")
    private UUID requestedById;

    @Column(name = "status")
    private String status;

    @Column(name = "dispatch_mode")
    private String dispatchMode;

    @Column(name = "external_task_id")
    private String externalTaskId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot")
    private JsonNode contextSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload")
    private JsonNode requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload")
    private JsonNode resultPayload;

    @Column(name = "queued_at")
    private OffsetDateTime queuedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(UUID workItemId) {
        this.workItemId = workItemId;
    }

    public UUID getAgentProfileId() {
        return agentProfileId;
    }

    public void setAgentProfileId(UUID agentProfileId) {
        this.agentProfileId = agentProfileId;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public void setProviderId(UUID providerId) {
        this.providerId = providerId;
    }

    public UUID getRequestedById() {
        return requestedById;
    }

    public void setRequestedById(UUID requestedById) {
        this.requestedById = requestedById;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDispatchMode() {
        return dispatchMode;
    }

    public void setDispatchMode(String dispatchMode) {
        this.dispatchMode = dispatchMode;
    }

    public String getExternalTaskId() {
        return externalTaskId;
    }

    public void setExternalTaskId(String externalTaskId) {
        this.externalTaskId = externalTaskId;
    }

    public JsonNode getContextSnapshot() {
        return contextSnapshot;
    }

    public void setContextSnapshot(JsonNode contextSnapshot) {
        this.contextSnapshot = contextSnapshot;
    }

    public JsonNode getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(JsonNode requestPayload) {
        this.requestPayload = requestPayload;
    }

    public JsonNode getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(JsonNode resultPayload) {
        this.resultPayload = resultPayload;
    }

    public OffsetDateTime getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(OffsetDateTime queuedAt) {
        this.queuedAt = queuedAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public OffsetDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(OffsetDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public OffsetDateTime getCanceledAt() {
        return canceledAt;
    }

    public void setCanceledAt(OffsetDateTime canceledAt) {
        this.canceledAt = canceledAt;
    }
}
