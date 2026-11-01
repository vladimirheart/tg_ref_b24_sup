package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** Read-only, uncached sender admission. No local fallback and no internal retries. */
@Service
public class PanelOutboundSenderAuthorizationClient {
    private final IntegrationPanelApiProperties properties;
    private final ObjectMapper mapper;
    private final PanelApiRequestHeadersFactory headers;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public PanelOutboundSenderAuthorizationClient(IntegrationPanelApiProperties properties, ObjectMapper mapper,
                                                   PanelApiRequestHeadersFactory headers) {
        this.properties = properties; this.mapper = mapper; this.headers = headers;
    }
    public boolean authorize(OutboundSenderIdentity identity) {
        if (!properties.isConfigured() || identity == null) throw new IllegalStateException("Sender authorization requires the internal panel API.");
        try {
            URI uri = URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/internal/api/bot/outbound/authorize-sender");
            String body = mapper.writeValueAsString(identity);
            var builder = HttpRequest.newBuilder(uri).timeout(properties.getRequestTimeout())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.apply(builder::header, "POST", uri, body, null);
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IllegalStateException("Sender authorization API returned status " + response.statusCode());
            var decision = mapper.readTree(response.body());
            if (decision == null || !decision.isObject() || !decision.path("allowed").isBoolean()
                || !decision.path("channelId").isIntegralNumber() || !decision.path("channelId").canConvertToLong()
                || decision.path("channelId").longValue() != identity.channelId()
                || !decision.path("platform").isTextual() || !identity.platform().equals(decision.path("platform").textValue())) {
                throw new IllegalStateException("Sender authorization response is missing or inconsistent.");
            }
            return decision.path("allowed").booleanValue();
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Sender authorization API is unavailable.", failure);
        }
    }
}
