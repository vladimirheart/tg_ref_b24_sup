package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class WorkspaceGuardrailWebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceGuardrailWebhookDeliveryService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WorkspaceGuardrailWebhookDeliveryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public boolean send(String webhookUrl, Map<String, Object> payload, int timeoutMs) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Workspace guardrail webhook sent successfully: status={}.", response.statusCode());
                return true;
            }
            log.warn("Workspace guardrail webhook failed: status={}, body={}", response.statusCode(), truncate(response.body(), 300));
            return false;
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize workspace guardrail webhook payload: {}", ex.getMessage());
            return false;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Unable to send workspace guardrail webhook: {}", ex.getMessage());
            return false;
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid workspace guardrail webhook URL: {}", ex.getMessage());
            return false;
        }
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
