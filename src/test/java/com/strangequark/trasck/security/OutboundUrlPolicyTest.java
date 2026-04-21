package com.strangequark.trasck.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OutboundUrlPolicyTest {

    @Test
    void blocksLocalPrivateAndMetadataUrlsByDefault() {
        OutboundUrlPolicy policy = new OutboundUrlPolicy("");

        assertThatThrownBy(() -> policy.validateHttpUrl("http://localhost:8080/hook", "url"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked");
        assertThatThrownBy(() -> policy.validateHttpUrl("http://127.0.0.1:8080/hook", "url"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked private or local network");
        assertThatThrownBy(() -> policy.validateHttpUrl("http://169.254.169.254/latest/meta-data", "url"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked private or local network");
    }

    @Test
    void permitsExternalHttpUrlsAndExplicitlyAllowlistedLocalHosts() {
        assertThatCode(() -> new OutboundUrlPolicy("").validateHttpUrl("https://example.com/hook", "url"))
                .doesNotThrowAnyException();
        assertThatCode(() -> new OutboundUrlPolicy("127.0.0.1").validateHttpUrl("http://127.0.0.1:8080/hook", "url"))
                .doesNotThrowAnyException();
    }

    @Test
    void permitsAllowlistedWildcardHostsAndCidrs() {
        OutboundUrlPolicy policy = new OutboundUrlPolicy("*.trusted.example,10.42.0.0/16");

        assertThatCode(() -> policy.validateHttpUrl("https://worker.trusted.example/hook", "url"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validateHttpUrl("http://10.42.7.15/hook", "url"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateHttpUrl("http://10.43.7.15/hook", "url"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked private or local network");
    }
}
