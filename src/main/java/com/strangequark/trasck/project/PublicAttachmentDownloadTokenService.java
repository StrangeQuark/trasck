package com.strangequark.trasck.project;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicAttachmentDownloadTokenService {

    private final byte[] signingSecret;
    private final Duration tokenTtl;

    public PublicAttachmentDownloadTokenService(
            @Value("${trasck.security.jwt-secret:dev-only-change-me-dev-only-change-me-dev-only-change-me-32}") String signingSecret,
            @Value("${trasck.public-download.token-ttl:PT5M}") String tokenTtl
    ) {
        byte[] secretBytes = signingSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("trasck.security.jwt-secret must be at least 32 bytes for public attachment download signing");
        }
        this.signingSecret = secretBytes.clone();
        this.tokenTtl = Duration.parse(tokenTtl);
    }

    SignedDownload issue(UUID projectId, UUID workItemId, UUID attachmentId) {
        Instant expiresAt = Instant.now().plus(tokenTtl);
        String payload = String.join(":",
                projectId.toString(),
                workItemId.toString(),
                attachmentId.toString(),
                Long.toString(expiresAt.getEpochSecond())
        );
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return new SignedDownload(
                encodedPayload + "." + signature(encodedPayload),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        );
    }

    void verify(String token, UUID projectId, UUID workItemId, UUID attachmentId) {
        if (token == null || token.isBlank()) {
            throw forbidden();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw forbidden();
        }
        String expectedSignature = signature(parts[0]);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw forbidden();
        }
        String payload = decodePayload(parts[0]);
        String[] values = payload.split(":", -1);
        if (values.length != 4) {
            throw forbidden();
        }
        try {
            UUID tokenProjectId = UUID.fromString(values[0]);
            UUID tokenWorkItemId = UUID.fromString(values[1]);
            UUID tokenAttachmentId = UUID.fromString(values[2]);
            long expiresAtEpochSecond = Long.parseLong(values[3]);
            if (!projectId.equals(tokenProjectId)
                    || !workItemId.equals(tokenWorkItemId)
                    || !attachmentId.equals(tokenAttachmentId)
                    || Instant.now().isAfter(Instant.ofEpochSecond(expiresAtEpochSecond))) {
                throw forbidden();
            }
        } catch (IllegalArgumentException ex) {
            throw forbidden();
        }
    }

    private String signature(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            return base64Url(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 is required", ex);
        }
    }

    private String decodePayload(String encodedPayload) {
        try {
            return new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw forbidden();
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid public attachment download token");
    }

    public record SignedDownload(String token, OffsetDateTime expiresAt) {
    }
}
