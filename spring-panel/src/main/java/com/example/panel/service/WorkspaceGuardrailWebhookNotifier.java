package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WorkspaceGuardrailWebhookNotifier {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceGuardrailWebhookNotifier.class);

    private final SharedConfigService sharedConfigService;
    private final DialogService dialogService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicReference<Instant> lastSentAt = new AtomicReference<>();
    private final AtomicReference<String> lastPayloadFingerprint = new AtomicReference<>("");

    public WorkspaceGuardrailWebhookNotifier(SharedConfigService sharedConfigService,
                                             DialogService dialogService,
                                             ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.dialogService = dialogService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Scheduled(fixedDelayString = "${panel.workspace-guardrails.webhook-check-interval-ms:300000}")
    public void notifyWhenGuardrailsRequireAttention() {
        Map<String, Object> config = resolveDialogConfig();
        if (!resolveBoolean(config, "workspace_guardrail_webhook_enabled", false)) {
            return;
        }
        String webhookUrl = trimToNull(String.valueOf(config.get("workspace_guardrail_webhook_url")));
        if (!StringUtils.hasText(webhookUrl)) {
            log.debug("Workspace guardrail webhook is enabled but URL is empty.");
            return;
        }

        int days = resolvePositiveInt(config, "workspace_guardrail_webhook_window_days", 7, 30);
        String experimentName = trimToNull((String) config.get("workspace_guardrail_webhook_experiment"));
        int minAlerts = resolvePositiveInt(config, "workspace_guardrail_webhook_min_alerts", 1, 20);
        int cooldownMinutes = resolvePositiveInt(config, "workspace_guardrail_webhook_cooldown_minutes", 30, 24 * 60);

        Map<String, Object> summary = dialogService.loadWorkspaceTelemetrySummary(days, experimentName);
        Map<String, Object> guardrails = extractMap(summary.get("guardrails"));
        String status = String.valueOf(guardrails.getOrDefault("status", "ok"));
        if (!"attention".equalsIgnoreCase(status)) {
            return;
        }

        List<Map<String, Object>> alerts = extractAlertList(guardrails.get("alerts"));
        if (alerts.size() < minAlerts) {
            return;
        }

        Instant now = Instant.now();
        Instant previousSentAt = lastSentAt.get();
        if (previousSentAt != null && now.isBefore(previousSentAt.plus(Duration.ofMinutes(cooldownMinutes)))) {
            return;
        }

        String fingerprint = buildFingerprint(days, experimentName, alerts);
        if (fingerprint.equals(lastPayloadFingerprint.get())) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "workspace_guardrails_attention");
        payload.put("generated_at", now.toString());
        payload.put("window_days", days);
        payload.put("experiment_name", experimentName);
        payload.put("alerts_total", alerts.size());
        payload.put("guardrails", guardrails);

        if (sendWebhook(webhookUrl, payload, resolvePositiveInt(config, "workspace_guardrail_webhook_timeout_ms", 4000, 15000))) {
            lastSentAt.set(now);
            lastPayloadFingerprint.set(fingerprint);
        }
    }

    private boolean sendWebhook(String webhookUrl, Map<String, Object> payload, int timeoutMs) {
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

    private String buildFingerprint(int days, String experimentName, List<Map<String, Object>> alerts) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("days", days);
        envelope.put("experiment", experimentName);
        envelope.put("alerts", alerts);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            return "fallback-" + alerts.hashCode();
        }
    }

    private List<Map<String, Object>> extractAlertList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> alert = new LinkedHashMap<>((Map<String, Object>) item);
                    return alert;
                })
                .toList();
    }

    private Map<String, Object> resolveDialogConfig() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object dialogConfigRaw = settings.get("dialog_config");
        if (dialogConfigRaw instanceof Map<?, ?> dialogConfig) {
            return dialogConfig.entrySet().stream()
                    .collect(LinkedHashMap::new,
                            (acc, entry) -> acc.put(String.valueOf(entry.getKey()), entry.getValue()),
                            LinkedHashMap::putAll);
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> extractMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        source.forEach((key, item) -> map.put(String.valueOf(key), item));
        return map;
    }

    private boolean resolveBoolean(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private int resolvePositiveInt(Map<String, Object> config, String key, int fallback, int max) {
        Object value = config.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed <= 0) {
                return fallback;
            }
            return Math.min(parsed, max);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
