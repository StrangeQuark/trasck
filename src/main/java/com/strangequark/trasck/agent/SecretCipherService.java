package com.strangequark.trasck.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SecretCipherService {

    private static final String PREFIX = "aesgcm:v1:";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipherService(@Value("${trasck.secrets.encryption-key}") String keyMaterial) {
        this.key = new SecretKeySpec(resolveKey(keyMaterial), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext is required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + base64(iv) + ":" + base64(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Secret encryption failed", ex);
        }
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || !encrypted.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported encrypted secret format");
        }
        String[] parts = encrypted.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Unsupported encrypted secret format");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Secret decryption failed", ex);
        }
    }

    private byte[] resolveKey(String keyMaterial) {
        String material = keyMaterial == null || keyMaterial.isBlank()
                ? "dev-only-change-me-dev-only-change-me-dev-only-change-me-32"
                : keyMaterial.trim();
        byte[] hex = decodedHexKeyOrNull(material);
        if (hex != null) {
            return hex;
        }
        byte[] decoded = decodedFixedKeyOrNull(material);
        if (decoded != null) {
            return decoded;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private byte[] decodedFixedKeyOrNull(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (validAesKeyLength(decoded.length)) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to URL-safe Base64 and hash-derived keys.
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (validAesKeyLength(decoded.length)) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to hash-derived keys.
        }
        return null;
    }

    private byte[] decodedHexKeyOrNull(String value) {
        if (value.length() % 2 != 0 || !validAesKeyLength(value.length() / 2)) {
            return null;
        }
        byte[] decoded = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            decoded[i / 2] = (byte) ((high << 4) + low);
        }
        return decoded;
    }

    private boolean validAesKeyLength(int length) {
        return Arrays.asList(16, 24, 32).contains(length);
    }

    private String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
