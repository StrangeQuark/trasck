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
@Table(name = "webhooks")
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "name")
    private String name;

    @Column(name = "url")
    private String url;

    @Column(name = "secret_hash")
    private String secretHash;

    @Column(name = "secret_encrypted")
    private String secretEncrypted;

    @Column(name = "secret_key_id")
    private String secretKeyId;

    @Column(name = "previous_secret_hash")
    private String previousSecretHash;

    @Column(name = "previous_secret_encrypted")
    private String previousSecretEncrypted;

    @Column(name = "previous_secret_key_id")
    private String previousSecretKeyId;

    @Column(name = "secret_rotated_at")
    private OffsetDateTime secretRotatedAt;

    @Column(name = "previous_secret_expires_at")
    private OffsetDateTime previousSecretExpiresAt;

    @Column(name = "previous_secret_overlap_seconds")
    private Long previousSecretOverlapSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_types")
    private JsonNode eventTypes;

    @Column(name = "enabled")
    private Boolean enabled;


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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public void setSecretEncrypted(String secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }

    public String getSecretKeyId() {
        return secretKeyId;
    }

    public void setSecretKeyId(String secretKeyId) {
        this.secretKeyId = secretKeyId;
    }

    public String getPreviousSecretHash() {
        return previousSecretHash;
    }

    public void setPreviousSecretHash(String previousSecretHash) {
        this.previousSecretHash = previousSecretHash;
    }

    public String getPreviousSecretEncrypted() {
        return previousSecretEncrypted;
    }

    public void setPreviousSecretEncrypted(String previousSecretEncrypted) {
        this.previousSecretEncrypted = previousSecretEncrypted;
    }

    public String getPreviousSecretKeyId() {
        return previousSecretKeyId;
    }

    public void setPreviousSecretKeyId(String previousSecretKeyId) {
        this.previousSecretKeyId = previousSecretKeyId;
    }

    public OffsetDateTime getSecretRotatedAt() {
        return secretRotatedAt;
    }

    public void setSecretRotatedAt(OffsetDateTime secretRotatedAt) {
        this.secretRotatedAt = secretRotatedAt;
    }

    public OffsetDateTime getPreviousSecretExpiresAt() {
        return previousSecretExpiresAt;
    }

    public void setPreviousSecretExpiresAt(OffsetDateTime previousSecretExpiresAt) {
        this.previousSecretExpiresAt = previousSecretExpiresAt;
    }

    public Long getPreviousSecretOverlapSeconds() {
        return previousSecretOverlapSeconds;
    }

    public void setPreviousSecretOverlapSeconds(Long previousSecretOverlapSeconds) {
        this.previousSecretOverlapSeconds = previousSecretOverlapSeconds;
    }

    public JsonNode getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(JsonNode eventTypes) {
        this.eventTypes = eventTypes;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
