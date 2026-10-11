package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanelTicketClientsExactLegacyIdTest {

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private PanelTicketReadClient readClient;
    private PanelTicketWriteClient writeClient;

    @BeforeEach
    void startLocalPanelApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String requestPath = exchange.getRequestURI().getRawPath();
            requests.add(exchange.getRequestMethod() + " " + requestPath);
            exchange.getRequestBody().readAllBytes();
            String body;
            if (requestPath.endsWith("/request-number")) {
                body = "{\"ticketId\":\" T-EXACT \",\"requestNumber\":\"N-19\"}";
            } else if ("GET".equals(exchange.getRequestMethod())) {
                body = "{\"userId\":19,\"ticketId\":\" T-EXACT \",\"status\":\"open\"}";
            } else {
                body = "{\"updated\":true,\"exists\":true}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var response = exchange.getResponseBody()) {
                response.write(bytes);
            }
        });
        server.start();

        IntegrationPanelApiProperties properties = new IntegrationPanelApiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setToken("test-only-panel-token");
        properties.setRequestSigningEnabled(false);
        properties.setRetryAttempts(1);
        PanelApiRequestHeadersFactory headers = new PanelApiRequestHeadersFactory(properties);
        ObjectMapper mapper = new ObjectMapper();
        readClient = new PanelTicketReadClient(properties, mapper, headers);
        writeClient = new PanelTicketWriteClient(properties, mapper, headers);
    }

    @AfterEach
    void stopLocalPanelApi() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readPathsKeepPaddedAndPlainLegacyIdsDifferent() {
        assertThat(readClient.findTicket(" T-EXACT ")).isPresent();
        assertThat(readClient.resolveRequestNumber(" T-EXACT ")).contains("N-19");
        assertThat(readClient.findTicket("T-EXACT")).isPresent();
        assertThat(requests).containsExactly(
            "GET /internal/api/bot/tickets/%20T-EXACT%20",
            "GET /internal/api/bot/tickets/%20T-EXACT%20/request-number",
            "GET /internal/api/bot/tickets/T-EXACT"
        );
    }

    @Test
    void mutationPathsKeepPaddedLegacyIdWithoutRewritingTextOrOperator() {
        assertThat(writeClient.reopenTicket(" T-EXACT ", "operator ")).isTrue();
        assertThat(writeClient.registerActivity(" T-EXACT ", "client")).isTrue();
        assertThat(writeClient.clearActivity(" T-EXACT ")).isTrue();
        assertThat(writeClient.recordOperatorRelay(" T-EXACT ", "message", 19L, null, "operator")).isTrue();
        assertThat(writeClient.markOperatorMessageEdited(" T-EXACT ", 29L, "edited", "operator")).isTrue();
        assertThat(requests).containsExactly(
            "POST /internal/api/bot/tickets/%20T-EXACT%20/reopen",
            "PUT /internal/api/bot/tickets/%20T-EXACT%20/activity",
            "DELETE /internal/api/bot/tickets/%20T-EXACT%20/activity",
            "POST /internal/api/bot/tickets/%20T-EXACT%20/operator-relay",
            "PUT /internal/api/bot/tickets/%20T-EXACT%20/operator-messages/29"
        );
    }

    @Test
    void blankIdsNeverContactPanelApi() {
        assertThat(readClient.findTicket("   ")).isEmpty();
        assertThat(readClient.resolveRequestNumber("   ")).isEmpty();
        assertThat(writeClient.reopenTicket("   ")).isFalse();
        assertThat(writeClient.registerActivity("   ", "client")).isFalse();
        assertThat(writeClient.clearActivity("   ")).isFalse();
        assertThat(writeClient.recordOperatorRelay("   ", "hello", 3L, null, "operator")).isFalse();
        assertThat(writeClient.markOperatorMessageEdited("   ", 4L, "hello", "operator")).isFalse();
        assertThat(requests).isEmpty();
    }
}
