package com.strangequark.trasck.activity.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class S3CompatibleAttachmentStorageProvider implements AttachmentStorageProvider {

    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);
    private static final String AWS4_ALGORITHM = "AWS4-HMAC-SHA256";

    private final HttpClient httpClient;

    public S3CompatibleAttachmentStorageProvider() {
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public Set<String> providerKeys() {
        return Set.of("s3", "s3-compatible");
    }

    @Override
    public void store(AttachmentStorageConfig config, AttachmentStorageWrite write) {
        HttpResponse<byte[]> response = send(config, "PUT", objectUri(config, write.storageKey()), write.content(), write.contentType());
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw storageFailure("write", response.statusCode());
        }
    }

    @Override
    public byte[] read(AttachmentStorageConfig config, String storageKey) {
        HttpResponse<byte[]> response = send(config, "GET", objectUri(config, storageKey), new byte[0], null);
        if (response.statusCode() == 404) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file not found");
        }
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw storageFailure("read", response.statusCode());
        }
        return response.body();
    }

    @Override
    public void delete(AttachmentStorageConfig config, String storageKey) {
        HttpResponse<byte[]> response = send(config, "DELETE", objectUri(config, storageKey), new byte[0], null);
        if (response.statusCode() != 404 && (response.statusCode() < 200 || response.statusCode() > 299)) {
            throw storageFailure("delete", response.statusCode());
        }
    }

    private HttpResponse<byte[]> send(AttachmentStorageConfig config, String method, URI uri, byte[] content, String contentType) {
        try {
            byte[] body = content == null ? new byte[0] : content;
            String payloadHash = sha256Hex(body);
            Instant now = Instant.now();
            String amzDate = AMZ_DATE.format(now);
            String dateStamp = DATE_STAMP.format(now);
            String region = requiredConfigText(config, "region");
            String service = text(config.getConfig(), "service");
            if (service == null) {
                service = "s3";
            }

            Map<String, String> headers = new TreeMap<>();
            headers.put("host", hostHeader(uri));
            headers.put("x-amz-content-sha256", payloadHash);
            headers.put("x-amz-date", amzDate);
            if (contentType != null && !contentType.isBlank()) {
                headers.put("content-type", contentType);
            }
            String signedHeaders = String.join(";", headers.keySet());
            String authorization = authorizationHeader(
                    config,
                    method,
                    uri,
                    headers,
                    signedHeaders,
                    payloadHash,
                    dateStamp,
                    amzDate,
                    region,
                    service
            );

            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .header("Authorization", authorization)
                    .header("x-amz-content-sha256", payloadHash)
                    .header("x-amz-date", amzDate);
            if (contentType != null && !contentType.isBlank()) {
                builder.header("Content-Type", contentType);
            }
            if ("GET".equals(method) || "DELETE".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "S3-compatible attachment storage request failed", ex);
        }
    }

    private String authorizationHeader(
            AttachmentStorageConfig config,
            String method,
            URI uri,
            Map<String, String> headers,
            String signedHeaders,
            String payloadHash,
            String dateStamp,
            String amzDate,
            String region,
            String service
    ) {
        String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String canonicalRequest = method + "\n"
                + canonicalPath(uri) + "\n"
                + canonicalQuery(uri) + "\n"
                + canonicalHeaders(headers) + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        String stringToSign = AWS4_ALGORITHM + "\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = signingKey(requiredConfigText(config, "secretAccessKey"), dateStamp, region, service);
        String signature = HexFormat.of().formatHex(hmac(signingKey, stringToSign));
        return AWS4_ALGORITHM
                + " Credential=" + requiredConfigText(config, "accessKeyId") + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private URI objectUri(AttachmentStorageConfig config, String storageKey) {
        String endpointText = requiredConfigText(config, "endpoint");
        URI endpoint = URI.create(endpointText);
        if (endpoint.getScheme() == null || endpoint.getHost() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "S3 endpoint must include scheme and host");
        }
        String bucket = requiredConfigText(config, "bucket");
        boolean pathStyle = booleanConfig(config.getConfig(), "pathStyle", true);
        String objectPath = pathStyle
                ? joinPath(endpoint.getRawPath(), bucket + "/" + storageKey)
                : joinPath(endpoint.getRawPath(), storageKey);
        String host = pathStyle ? endpoint.getHost() : bucket + "." + endpoint.getHost();
        try {
            return new URI(endpoint.getScheme(), null, host, endpoint.getPort(), objectPath, null, null);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid S3 attachment storage URI", ex);
        }
    }

    private String joinPath(String basePath, String objectPath) {
        String base = basePath == null || basePath.isBlank() || "/".equals(basePath) ? "" : basePath;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String object = objectPath;
        while (object.startsWith("/")) {
            object = object.substring(1);
        }
        return "/" + (base.isBlank() ? object : base.substring(1) + "/" + object);
    }

    private String canonicalPath(URI uri) {
        String path = uri.getRawPath();
        return path == null || path.isBlank() ? "/" : path;
    }

    private String canonicalQuery(URI uri) {
        String query = uri.getRawQuery();
        return query == null ? "" : query;
    }

    private String canonicalHeaders(Map<String, String> headers) {
        StringBuilder builder = new StringBuilder();
        headers.forEach((name, value) -> builder.append(name)
                .append(":")
                .append(value.trim().replaceAll("\\s+", " "))
                .append("\n"));
        return builder.toString();
    }

    private String hostHeader(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1 || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443)) {
            return host;
        }
        return host + ":" + port;
    }

    private byte[] signingKey(String secretAccessKey, String dateStamp, String region, String service) {
        byte[] dateKey = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] regionKey = hmac(dateKey, region);
        byte[] serviceKey = hmac(regionKey, service);
        return hmac(serviceKey, "aws4_request");
    }

    private byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 is required", ex);
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private ResponseStatusException storageFailure(String operation, int statusCode) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "S3-compatible attachment storage failed to " + operation + " file, status " + statusCode
        );
    }

    private String requiredConfigText(AttachmentStorageConfig config, String key) {
        String value = text(config.getConfig(), key);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment storage config missing " + key);
        }
        return value;
    }

    private String text(JsonNode config, String key) {
        if (config == null || config.isNull()) {
            return null;
        }
        JsonNode value = config.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private boolean booleanConfig(JsonNode config, String key, boolean defaultValue) {
        if (config == null || config.isNull() || config.get(key) == null) {
            return defaultValue;
        }
        return config.get(key).asBoolean(defaultValue);
    }
}
