package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SlaEscalationWebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationWebhookDeliveryService.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper;

    public SlaEscalationWebhookDeliveryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> resolveWebhookUrls(Map<String, Object> dialogConfig) {
        return resolveWebhookEndpoints(dialogConfig).stream().map(SlaEscalationWebhookNotifier.WebhookEndpoint::url).toList();
    }

    public List<SlaEscalationWebhookNotifier.WebhookEndpoint> resolveWebhookEndpoints(Map<String, Object> dialogConfig) {
        if (dialogConfig == null || dialogConfig.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, SlaEscalationWebhookNotifier.WebhookEndpoint> uniqueEndpoints = new LinkedHashMap<>();
        Object rawEndpoints = dialogConfig.get("sla_critical_escalation_webhooks");
        if (rawEndpoints instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> endpointMap)) {
                    continue;
                }
                String url = trimToNull(String.valueOf(endpointMap.get("url")));
                if (url == null || !resolveBoolean(convertToObjectMap(endpointMap), "enabled", true)) {
                    continue;
                }
                uniqueEndpoints.putIfAbsent(url, new SlaEscalationWebhookNotifier.WebhookEndpoint(url, extractHeaders(endpointMap.get("headers"))));
            }
        }

        LinkedHashSet<String> legacyUrls = new LinkedHashSet<>();
        Object rawList = dialogConfig.get("sla_critical_escalation_webhook_urls");
        if (rawList instanceof List<?> list) {
            for (Object item : list) {
                collectWebhookUrl(String.valueOf(item), legacyUrls);
            }
        }
        collectWebhookUrl(String.valueOf(dialogConfig.get("sla_critical_escalation_webhook_url")), legacyUrls);
        for (String url : legacyUrls) {
            uniqueEndpoints.putIfAbsent(url, new SlaEscalationWebhookNotifier.WebhookEndpoint(url, Collections.emptyMap()));
        }
        return new ArrayList<>(uniqueEndpoints.values());
    }

    public boolean sendWebhookFanout(List<SlaEscalationWebhookNotifier.WebhookEndpoint> webhookEndpoints,
                                     Map<String, Object> payload,
                                     int timeoutMs,
                                     int retryAttempts,
                                     int retryBackoffMs) {
        boolean atLeastOneSuccess = false;
        for (SlaEscalationWebhookNotifier.WebhookEndpoint endpoint : webhookEndpoints) {
            if (sendWebhookWithRetry(endpoint, payload, timeoutMs, retryAttempts, retryBackoffMs)) {
                atLeastOneSuccess = true;
                continue;
            }
            log.warn("SLA escalation webhook endpoint failed: {}", endpoint.url());
        }
        return atLeastOneSuccess;
    }

    private void collectWebhookUrl(String rawValue, Set<String> uniqueUrls) {
        String normalized = trimToNull(rawValue);
        if (normalized == null) {
            return;
        }
        String[] split = normalized.split("[,;\\n]");
        for (String chunk : split) {
            String url = trimToNull(chunk);
            if (url == null) {
                continue;
            }
            uniqueUrls.add(url);
        }
    }

    private boolean sendWebhookWithRetry(SlaEscalationWebhookNotifier.WebhookEndpoint endpoint,
                                         Map<String, Object> payload,
                                         int timeoutMs,
                                         int retryAttempts,
                                         int retryBackoffMs) {
        int attempts = Math.max(1, retryAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (sendWebhook(endpoint, payload, timeoutMs)) {
                return true;
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep((long) retryBackoffMs * attempt);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private boolean sendWebhook(SlaEscalationWebhookNotifier.WebhookEndpoint endpoint,
                                Map<String, Object> payload,
                                int timeoutMs) {
        try {
            URI uri = new URI(endpoint.url());
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json");
            endpoint.headers().forEach(builder::header);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return true;
            }
            log.warn("SLA escalation webhook responded with status={} body={}", statusCode, truncate(response.body(), 300));
            return false;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            log.warn("Invalid SLA escalation webhook URL: {}", endpoint.url(), ex);
            return false;
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize SLA webhook payload", ex);
            return false;
        } catch (IOException ex) {
            log.warn("I/O failure while sending SLA webhook to {}", endpoint.url(), ex);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending SLA webhook to {}", endpoint.url(), ex);
            return false;
        }
    }

    private Map<String, String> extractHeaders(Object rawHeaders) {
        if (!(rawHeaders instanceof Map<?, ?> headerMap)) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headerMap.forEach((rawKey, rawValue) -> {
            String key = trimToNull(String.valueOf(rawKey));
            String value = trimToNull(String.valueOf(rawValue));
            if (key != null && value != null) {
                headers.put(key, value);
            }
        });
        return headers;
    }

    private Map<String, Object> convertToObjectMap(Map<?, ?> rawMap) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private boolean resolveBoolean(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
