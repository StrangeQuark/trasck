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
@Table(name = "agent_task_events")
public class AgentTaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "agent_task_id")
    private UUID agentTaskId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "severity")
    private String severity;

    @Column(name = "message")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private JsonNode metadata;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAgentTaskId() {
        return agentTaskId;
    }

    public void setAgentTaskId(UUID agentTaskId) {
        this.agentTaskId = agentTaskId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
