package com.strangequark.trasck.integration;

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
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "webhook_id")
    private UUID webhookId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "signature_key_id")
    private String signatureKeyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private JsonNode payload;

    @Column(name = "status")
    private String status;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWebhookId() {
        return webhookId;
    }

    public void setWebhookId(UUID webhookId) {
        this.webhookId = webhookId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSignatureKeyId() {
        return signatureKeyId;
    }

    public void setSignatureKeyId(String signatureKeyId) {
        this.signatureKeyId = signatureKeyId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public OffsetDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(OffsetDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
