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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PanelRuntimeConfigClientTest {

    private HttpServer server;
    private PanelRuntimeConfigClient client;
    private volatile int responseStatus;
    private final java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
    private volatile String overrideBody;

    @BeforeEach
    void startLocalPanelApi() throws IOException {
        responseStatus = 200;
        overrideBody=null;requests.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/api/bot/channels/8/runtime-config", exchange -> {
            requests.incrementAndGet();
            byte[] body = """
                {"channelId":8,"scopeKind":"GENERAL","scopeSelectionSource":"GENERAL_CHANNEL",
                 "questionTemplateId":"q-general","templateSha256":"hash",
                 "botSettings":{"active_template_id":"q-general"},
                 "locationTree":{},"presetDefinitions":{}}
                """.getBytes(StandardCharsets.UTF_8);
            if(overrideBody!=null) body=overrideBody.getBytes(StandardCharsets.UTF_8);
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

    @Test void cachedRuntimeCannotMaskChannelRebind() {
        assertThat(client.findByChannelId(8L)).isPresent();
        responseStatus=409;
        assertThatThrownBy(()->client.findByChannelId(8L)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        assertThat(requests.get()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,404,429,500,503})
    void configuredPanelFailureNeverReturnsLegacyFallback(int status) {
        responseStatus=status;
        assertThatThrownBy(()->client.findByChannelId(8L)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
    @ParameterizedTest @ValueSource(strings={"null","{}","not-json","{\"channelId\":999,\"botSettings\":{},\"locationTree\":{},\"presetDefinitions\":{}}","{\"channelId\":8,\"scopeKind\":\"BUSINESS\",\"botSettings\":{},\"locationTree\":{},\"presetDefinitions\":{}}"})
    void malformedOrUnknownPrimaryRuntimeNeverBecomesLegacy(String body) {
        overrideBody=body;
        assertThatThrownBy(()->client.findByChannelId(8L)).isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
    }
}
