package com.strangequark.trasck.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "email_provider_settings")
public class EmailProviderSettings {

    @Id
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(name = "provider")
    private String provider;

    @Column(name = "from_email")
    private String fromEmail;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_password_encrypted")
    private String smtpPasswordEncrypted;

    @Column(name = "smtp_start_tls_enabled")
    private Boolean smtpStartTlsEnabled;

    @Column(name = "smtp_auth_enabled")
    private Boolean smtpAuthEnabled;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public Integer getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPasswordEncrypted() {
        return smtpPasswordEncrypted;
    }

    public void setSmtpPasswordEncrypted(String smtpPasswordEncrypted) {
        this.smtpPasswordEncrypted = smtpPasswordEncrypted;
    }

    public Boolean getSmtpStartTlsEnabled() {
        return smtpStartTlsEnabled;
    }

    public void setSmtpStartTlsEnabled(Boolean smtpStartTlsEnabled) {
        this.smtpStartTlsEnabled = smtpStartTlsEnabled;
    }

    public Boolean getSmtpAuthEnabled() {
        return smtpAuthEnabled;
    }

    public void setSmtpAuthEnabled(Boolean smtpAuthEnabled) {
        this.smtpAuthEnabled = smtpAuthEnabled;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
