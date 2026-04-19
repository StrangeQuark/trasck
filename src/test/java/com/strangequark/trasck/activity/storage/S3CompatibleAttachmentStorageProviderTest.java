package com.strangequark.trasck.activity.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.strangequark.trasck.activity.AttachmentStorageConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class S3CompatibleAttachmentStorageProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private HttpServer server;
    private String endpoint;

    @BeforeEach
    void startFakeS3() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handleObjectRequest);
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFakeS3() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void storesReadsAndDeletesAgainstControlledS3CompatibleFake() {
        S3CompatibleAttachmentStorageProvider provider = new S3CompatibleAttachmentStorageProvider();
        AttachmentStorageConfig config = storageConfig("bucket-a");
        byte[] content = "S3 fake attachment".getBytes(StandardCharsets.UTF_8);

        provider.store(config, new AttachmentStorageWrite("workspace/file.txt", "text/plain", content));
        assertThat(objects).containsKey("/bucket-a/workspace/file.txt");

        assertThat(provider.read(config, "workspace/file.txt")).isEqualTo(content);

        provider.delete(config, "workspace/file.txt");
        assertThat(objects).doesNotContainKey("/bucket-a/workspace/file.txt");
        assertThatThrownBy(() -> provider.read(config, "workspace/file.txt"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    private AttachmentStorageConfig storageConfig(String bucket) {
        ObjectNode configJson = objectMapper.createObjectNode()
                .put("endpoint", endpoint)
                .put("bucket", bucket)
                .put("region", "us-east-1")
                .put("accessKeyId", "test-access-key")
                .put("secretAccessKey", "test-secret-key")
                .put("pathStyle", true);
        AttachmentStorageConfig config = new AttachmentStorageConfig();
        config.setProvider("s3-compatible");
        config.setConfig(configJson);
        return config;
    }

    private void handleObjectRequest(HttpExchange exchange) {
        try {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("AWS4-HMAC-SHA256");
            String path = exchange.getRequestURI().getPath();
            switch (exchange.getRequestMethod()) {
                case "PUT" -> {
                    byte[] body = exchange.getRequestBody().readAllBytes();
                    objects.put(path, body);
                    exchange.sendResponseHeaders(200, -1);
                }
                case "GET" -> {
                    byte[] body = objects.get(path);
                    if (body == null) {
                        exchange.sendResponseHeaders(404, -1);
                    } else {
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                    }
                }
                case "DELETE" -> {
                    objects.remove(path);
                    exchange.sendResponseHeaders(204, -1);
                }
                default -> exchange.sendResponseHeaders(405, -1);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            exchange.close();
        }
    }
}
