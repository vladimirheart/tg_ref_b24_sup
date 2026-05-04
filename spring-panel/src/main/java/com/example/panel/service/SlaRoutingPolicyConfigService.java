package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

@Service
public class SlaRoutingPolicyConfigService {

    private final SlaRoutingRuleScalarParserService scalarParserService;

    public SlaRoutingPolicyConfigService(SlaRoutingRuleScalarParserService scalarParserService) {
        this.scalarParserService = scalarParserService;
    }

    public SlaRoutingPolicyConfigService() {
        this(new SlaRoutingRuleScalarParserService());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> extractDialogConfig(Map<String, Object> settings) {
        if (settings == null) {
            return Map.of();
        }
        Object value = settings.get("dialog_config");
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    public boolean resolveBoolean(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) return true;
            if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) return false;
        }
        return fallback;
    }

    public int resolvePositiveInt(Map<String, Object> config, String key, int fallback, int maxValue) {
        Object value = config.get(key);
        int parsed = fallback;
        if (value instanceof Number number) parsed = number.intValue();
        else if (value instanceof String text) {
            try {
                parsed = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                parsed = fallback;
            }
        }
        if (parsed <= 0) return fallback;
        return Math.min(parsed, maxValue);
    }

    public SlaEscalationWebhookNotifier.SlaOrchestrationMode resolveOrchestrationMode(Object rawMode) {
        String normalized = trimToNull(String.valueOf(rawMode));
        if (normalized == null) return SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT;
        return switch (normalized.trim().toLowerCase(Locale.ROOT)) {
            case "monitor", "observe", "dry_run" -> SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR;
            case "autopilot", "full", "auto" -> SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT;
            default -> SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST;
        };
    }

    public Long resolveMinutesLeft(String createdAt, int targetMinutes, long nowMs) {
        Instant created = parseInstant(createdAt);
        if (created == null) return null;
        return Math.floorDiv(created.toEpochMilli() + targetMinutes * 60_000L - nowMs, 60_000L);
    }

    public String normalizeUtcTimestamp(String rawValue) {
        Instant parsed = parseInstant(rawValue);
        return parsed == null ? null : parsed.toString();
    }

    public String normalizeLifecycleState(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized == null) {
                continue;
            }
            String lowered = normalized.toLowerCase(Locale.ROOT);
            switch (lowered) {
                case "open", "new", "waiting_operator", "waiting_client" -> {
                    return "open";
                }
                case "closed", "resolved", "auto_closed" -> {
                    return "closed";
                }
                default -> {
                    return lowered;
                }
            }
        }
        return "unknown";
    }

    public String trimToNull(String value) {
        return scalarParserService.trimToNull(value);
    }

    private Instant parseInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        try {
            return Instant.parse(rawValue.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(rawValue.trim()).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }
}
