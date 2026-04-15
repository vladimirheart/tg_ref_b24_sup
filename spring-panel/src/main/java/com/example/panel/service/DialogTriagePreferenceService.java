package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DialogTriagePreferenceService {

    private final UiPreferenceService uiPreferenceService;
    private final SharedConfigService sharedConfigService;

    public DialogTriagePreferenceService(UiPreferenceService uiPreferenceService,
                                         SharedConfigService sharedConfigService) {
        this.uiPreferenceService = uiPreferenceService;
        this.sharedConfigService = sharedConfigService;
    }

    public Map<String, Object> loadForOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            return Map.of();
        }
        Map<String, Object> serverBacked = uiPreferenceService.loadDialogsTriageForUser(operator);
        if (!serverBacked.isEmpty()) {
            return serverBacked;
        }
        Map<String, Object> legacyPreferences = loadLegacyPreferences(operator);
        if (!legacyPreferences.isEmpty()) {
            return uiPreferenceService.saveDialogsTriageForUser(operator, legacyPreferences);
        }
        return Map.of();
    }

    public Map<String, Object> saveForOperator(String operator,
                                               String view,
                                               String sortMode,
                                               Integer slaWindowMinutes,
                                               String pageSize) {
        if (!StringUtils.hasText(operator)) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("view", view);
        payload.put("sort_mode", sortMode);
        payload.put("page_size", pageSize);
        if (slaWindowMinutes != null) {
            payload.put("sla_window_minutes", slaWindowMinutes);
        }
        return uiPreferenceService.saveDialogsTriageForUser(operator, payload);
    }

    public String normalizeView(Object rawValue) {
        String value = trimToNull(String.valueOf(rawValue));
        if (!StringUtils.hasText(value)) {
            return "all";
        }
        return switch (value.toLowerCase()) {
            case "active", "new", "unassigned", "overdue", "sla_critical", "escalation_required" -> value.toLowerCase();
            default -> "all";
        };
    }

    public String normalizeSortMode(Object rawValue) {
        String value = trimToNull(String.valueOf(rawValue));
        if (!StringUtils.hasText(value)) {
            return "default";
        }
        return "sla_priority".equalsIgnoreCase(value) ? "sla_priority" : "default";
    }

    public Integer normalizeSlaWindowMinutes(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        int parsed;
        if (rawValue instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return switch (parsed) {
            case 15, 30, 60, 120, 240 -> parsed;
            default -> null;
        };
    }

    public String normalizePageSizePreference(Object rawValue) {
        String value = trimToNull(String.valueOf(rawValue));
        if (!StringUtils.hasText(value)) {
            return "20";
        }
        if ("all".equalsIgnoreCase(value)) {
            return "all";
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? String.valueOf(parsed) : "20";
        } catch (NumberFormatException ex) {
            return "20";
        }
    }

    private Map<String, Object> loadLegacyPreferences(String operator) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object rawDialogConfig = settings.get("dialog_config");
        if (!(rawDialogConfig instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Object rawByOperator = dialogConfig.get("workspace_triage_preferences_by_operator");
        if (!(rawByOperator instanceof Map<?, ?> byOperator)) {
            return Map.of();
        }
        Object rawPreferences = byOperator.get(operator);
        if (!(rawPreferences instanceof Map<?, ?> preferences)) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("view", normalizeView(preferences.get("view")));
        payload.put("sort_mode", normalizeSortMode(preferences.get("sort_mode")));
        payload.put("page_size", normalizePageSizePreference(preferences.get("page_size")));
        Integer slaWindowMinutes = normalizeSlaWindowMinutes(preferences.get("sla_window_minutes"));
        if (slaWindowMinutes != null) {
            payload.put("sla_window_minutes", slaWindowMinutes);
        }
        String updatedAtUtc = normalizeUtcTimestamp(preferences.get("updated_at_utc"));
        if (updatedAtUtc != null) {
            payload.put("updated_at_utc", updatedAtUtc);
        }
        return payload;
    }

    private String normalizeUtcTimestamp(Object rawValue) {
        String value = trimToNull(String.valueOf(rawValue));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
