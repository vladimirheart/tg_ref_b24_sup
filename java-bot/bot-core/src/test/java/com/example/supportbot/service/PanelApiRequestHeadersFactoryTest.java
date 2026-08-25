package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PanelApiRequestHeadersFactoryTest {

    @Test
    void applyAddsSignedHeadersAndIdempotencyKey() {
        IntegrationPanelApiProperties properties = new IntegrationPanelApiProperties();
        properties.setToken("panel-token");
        properties.setSignatureSecret("panel-secret");
        properties.setRequestSigningEnabled(true);
        PanelApiRequestHeadersFactory factory = new PanelApiRequestHeadersFactory(properties);

        Map<String, String> headers = new LinkedHashMap<>();
        factory.apply(headers::put, "POST", java.net.URI.create("http://127.0.0.1:8080/internal/api/bot/tickets/T-1/reopen"),
            "{\"userIdentity\":\"operator\"}", "idem-1");

        assertThat(headers).containsEntry(PanelApiRequestHeadersFactory.AUTH_HEADER, "panel-token");
        assertThat(headers).containsEntry(PanelApiRequestHeadersFactory.IDEMPOTENCY_HEADER, "idem-1");
        assertThat(headers.get(PanelApiRequestHeadersFactory.TIMESTAMP_HEADER)).isNotBlank();
        assertThat(headers.get(PanelApiRequestHeadersFactory.SIGNATURE_HEADER)).matches("[0-9a-f]{64}");
    }
}
