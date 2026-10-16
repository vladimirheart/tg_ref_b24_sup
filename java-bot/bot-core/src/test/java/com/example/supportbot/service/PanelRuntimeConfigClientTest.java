package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PanelRuntimeConfigClientTest {

    private HttpServer server;
    private PanelRuntimeConfigClient client;
    private volatile int responseStatus;

    @BeforeEach
    void startLocalPanelApi() throws IOException {
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/api/bot/channels/8/runtime-config", exchange -> {
            byte[] body = """
                {"channelId":8,"scopeKind":"GENERAL","scopeSelectionSource":"GENERAL_CHANNEL",
                 "questionTemplateId":"q-general","templateSha256":"hash",
                 "botSettings":{"active_template_id":"q-general"},
                 "locationTree":{},"presetDefinitions":{}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (var response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        IntegrationPanelApiProperties properties = new IntegrationPanelApiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("test-only-panel-token");
        properties.setRequestSigningEnabled(false);
        client = new PanelRuntimeConfigClient(properties, new ObjectMapper(),
            new PanelApiRequestHeadersFactory(properties));
    }

    @AfterEach
    void stopLocalPanelApi() {
        server.stop(0);
    }

    @Test
    void parsesGeneralChannelIdentityFromPrimaryRuntimeEndpoint() {
        var snapshot = client.findByChannelId(8L).orElseThrow();

        assertThat(snapshot.isGeneralChannel()).isTrue();
        assertThat(snapshot.questionTemplateId()).isEqualTo("q-general");
        assertThat(snapshot.templateSha256()).isEqualTo("hash");
    }

    @Test
    void conflictDoesNotFallBackToLocalRuntimeConfig() {
        responseStatus = 409;

        assertThatThrownBy(() -> client.findByChannelId(8L))
            .isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
}
