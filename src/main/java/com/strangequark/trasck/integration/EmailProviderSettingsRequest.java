package com.strangequark.trasck.integration;

public record EmailProviderSettingsRequest(
        String provider,
        String fromEmail,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpPassword,
        Boolean clearSmtpPassword,
        Boolean smtpStartTlsEnabled,
        Boolean smtpAuthEnabled,
        Boolean active
) {
}
