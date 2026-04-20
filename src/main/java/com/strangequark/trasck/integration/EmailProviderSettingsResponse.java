package com.strangequark.trasck.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EmailProviderSettingsResponse(
        UUID workspaceId,
        String provider,
        String fromEmail,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        Boolean smtpPasswordConfigured,
        Boolean smtpStartTlsEnabled,
        Boolean smtpAuthEnabled,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static EmailProviderSettingsResponse from(EmailProviderSettings settings) {
        return new EmailProviderSettingsResponse(
                settings.getWorkspaceId(),
                settings.getProvider(),
                settings.getFromEmail(),
                settings.getSmtpHost(),
                settings.getSmtpPort(),
                settings.getSmtpUsername(),
                settings.getSmtpPasswordEncrypted() != null && !settings.getSmtpPasswordEncrypted().isBlank(),
                settings.getSmtpStartTlsEnabled(),
                settings.getSmtpAuthEnabled(),
                settings.getActive(),
                settings.getCreatedAt(),
                settings.getUpdatedAt()
        );
    }
}
