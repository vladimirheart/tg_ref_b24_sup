package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.security.InternalBotApiProperties;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BotRunnerLifecycleClientTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void signedStartCarriesCommandIdentityAndAcceptsMatchingAcknowledgement() throws Exception {
        InternalBotApiProperties properties = new InternalBotApiProperties();
        properties.setToken("test-token");
        properties.setSignatureSecret("test-signature-secret");
        properties.setRequireRequestSignature(true);

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        when(response.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            captured.set(request);
            String commandId = request.headers()
                .firstValue(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER)
                .orElseThrow();
            when(response.body()).thenReturn("""
                {"commandId":"%s","channelId":11,"action":"start","success":true,"status":"running","startedAt":"2026-09-19T15:00:00Z","runnerInstanceId":"runner-1"}
                """.formatted(commandId));
            return response;
        });

        BotRunnerLifecycleClient client = new BotRunnerLifecycleClient(
            new ObjectMapper(),
            properties,
            "http://bot-runner:8080",
            Duration.ofSeconds(5),
            httpClient
        );

        BotLifecycleCommandResult result = client.start(11L);

        assertThat(result.success()).isTrue();
        assertThat(result.runnerInstanceId()).isEqualTo("runner-1");
        HttpRequest request = captured.get();
        assertThat(request.uri().toString()).isEqualTo("http://bot-runner:8080/internal/api/bot/runtime/11/start");
        assertThat(request.headers().firstValue(InternalBotApiRequestGuardService.AUTH_HEADER)).contains("test-token");
        assertThat(request.headers().firstValue(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER).orElse(""))
            .startsWith("bot-lifecycle:start:11:");
        assertThat(request.headers().firstValue(InternalBotApiRequestGuardService.TIMESTAMP_HEADER)).isPresent();
        assertThat(request.headers().firstValue(InternalBotApiRequestGuardService.SIGNATURE_HEADER).orElse(""))
            .matches("[0-9a-f]{64}");
    }
}
