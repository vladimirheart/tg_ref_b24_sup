package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkspaceGuardrailWebhookCommandService {

    private final SharedConfigService sharedConfigService;
    private final DialogWorkspaceTelemetrySummaryService dialogWorkspaceTelemetrySummaryService;
    private final ObjectMapper objectMapper;

    public WorkspaceGuardrailWebhookCommandService(SharedConfigService sharedConfigService,
                                                   DialogWorkspaceTelemetrySummaryService dialogWorkspaceTelemetrySummaryService,
                                                   ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.dialogWorkspaceTelemetrySummaryService = dialogWorkspaceTelemetrySummaryService;
        this.objectMapper = objectMapper;
    }

    public WorkspaceGuardrailWebhookCommand resolveCommand(Instant now,
                                                           Instant previousSentAt,
                                                           String previousFingerprint) {
        Map<String, Object> config = resolveDialogConfig();
        if (!resolveBoolean(config, "workspace_guardrail_webhook_enabled", false)) {
            return null;
        }
        String webhookUrl = trimToNull(String.valueOf(config.get("workspace_guardrail_webhook_url")));
        if (!StringUtils.hasText(webhookUrl)) {
            return null;
        }

        int days = resolvePositiveInt(config, "workspace_guardrail_webhook_window_days", 7, 30);
        String experimentName = trimToNull((String) config.get("workspace_guardrail_webhook_experiment"));
        int minAlerts = resolvePositiveInt(config, "workspace_guardrail_webhook_min_alerts", 1, 20);
        int cooldownMinutes = resolvePositiveInt(config, "workspace_guardrail_webhook_cooldown_minutes", 30, 24 * 60);
        int timeoutMs = resolvePositiveInt(config, "workspace_guardrail_webhook_timeout_ms", 4000, 15000);

        Map<String, Object> summary = dialogWorkspaceTelemetrySummaryService.loadSummary(days, experimentName);
        Map<String, Object> guardrails = extractMap(summary.get("guardrails"));
        String status = String.valueOf(guardrails.getOrDefault("status", "ok"));
        if (!"attention".equalsIgnoreCase(status)) {
            return null;
        }

        List<Map<String, Object>> alerts = extractAlertList(guardrails.get("alerts"));
        if (alerts.size() < minAlerts) {
            return null;
        }
        if (previousSentAt != null && now.isBefore(previousSentAt.plus(Duration.ofMinutes(cooldownMinutes)))) {
            return null;
        }

        String fingerprint = buildFingerprint(days, experimentName, alerts);
        if (fingerprint.equals(previousFingerprint)) {
            return null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "workspace_guardrails_attention");
        payload.put("generated_at", now.toString());
        payload.put("window_days", days);
        payload.put("experiment_name", experimentName);
        payload.put("alerts_total", alerts.size());
        payload.put("guardrails", guardrails);
        return new WorkspaceGuardrailWebhookCommand(webhookUrl, timeoutMs, fingerprint, payload);
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
                .map(item -> (Map<String, Object>) new LinkedHashMap<>((Map<String, Object>) item))
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

    public record WorkspaceGuardrailWebhookCommand(String webhookUrl,
                                                   int timeoutMs,
                                                   String fingerprint,
                                                   Map<String, Object> payload) {
    }
}
