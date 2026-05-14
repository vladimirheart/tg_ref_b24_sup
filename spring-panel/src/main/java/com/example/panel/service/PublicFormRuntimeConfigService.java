package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

@Service
public class PublicFormRuntimeConfigService {
    private static final int DEFAULT_ANSWERS_TOTAL_MAX_LENGTH = 6000;

    private final SharedConfigService sharedConfigService;

    public PublicFormRuntimeConfigService(SharedConfigService sharedConfigService) {
        this.sharedConfigService = sharedConfigService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> readDialogConfig() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object dialogConfig = settings.get("dialog_config");
        if (dialogConfig instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    public boolean readDialogConfigBoolean(String key, boolean defaultValue) {
        Object raw = readDialogConfig().get(key);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof String text) {
            return "true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim());
        }
        return defaultValue;
    }

    public int readDialogConfigInt(String key, int defaultValue, int minValue, int maxValue) {
        Object raw = readDialogConfig().get(key);
        int value = defaultValue;
        if (raw instanceof Number number) {
            value = number.intValue();
        } else if (raw instanceof String text) {
            try {
                value = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                value = defaultValue;
            }
        }
        return Math.max(minValue, Math.min(maxValue, value));
    }

    public double readDialogConfigDouble(String key, double defaultValue, double minValue, double maxValue) {
        Object value = readDialogConfig().get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            double parsed = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value).trim());
            if (!Double.isFinite(parsed) || parsed < minValue || parsed > maxValue) {
                return defaultValue;
            }
            return parsed;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public String readDialogConfigString(String key, String defaultValue) {
        Object raw = readDialogConfig().get(key);
        if (raw == null) {
            return defaultValue;
        }
        String value = raw.toString().trim();
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    public boolean isMetricsEnabled() {
        return readDialogConfigBoolean("public_form_metrics_enabled", true);
    }

    public int resolveAnswersPayloadMaxLength() {
        return readDialogConfigInt("public_form_answers_total_max_length", DEFAULT_ANSWERS_TOTAL_MAX_LENGTH, 200, 50000);
    }

    public boolean isSessionPollingEnabled() {
        return readDialogConfigBoolean("public_form_session_polling_enabled", true);
    }

    public int resolveSessionPollingIntervalSeconds() {
        return readDialogConfigInt("public_form_session_polling_interval_seconds", 15, 5, 300);
    }

    public String resolveUiLocale() {
        String configured = readDialogConfigString("public_form_default_locale", "auto");
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ru", "en", "auto" -> normalized;
            default -> "auto";
        };
    }

    public int normalizeDisabledStatus(int value) {
        return value == 410 ? 410 : 404;
    }
}
