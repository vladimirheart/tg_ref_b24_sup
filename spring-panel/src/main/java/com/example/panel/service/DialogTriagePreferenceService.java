package com.example.panel.service;

import com.example.panel.model.dialog.DialogListQuery;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogTriagePreferenceService {

    private final UiPreferenceService uiPreferenceService;

    public DialogTriagePreferenceService(UiPreferenceService uiPreferenceService) {
        this.uiPreferenceService = uiPreferenceService;
    }

    public Map<String, Object> loadForOperator(Authentication authentication) {
        if (!UiPreferenceService.isBoundAccount(authentication)) return Map.of();
        return uiPreferenceService.loadDialogsTriageForUser(authentication);
    }

    public DialogListQuery initialListQuery(Map<String, Object> preferences) {
        Map<String, Object> values = preferences == null ? Map.of() : preferences;
        int pageSize;
        try {
            pageSize = Integer.parseInt(normalizePageSizePreference(values.get("page_size")));
        } catch (NumberFormatException ex) {
            pageSize = DialogListQuery.DEFAULT_SIZE;
        }
        return new DialogListQuery(1, pageSize, "", "", normalizeView(values.get("view")),
                normalizeSlaWindowMinutes(values.get("sla_window_minutes")), normalizeSortMode(values.get("sort_mode")), List.of());
    }

    public Map<String, Object> saveForOperator(Authentication authentication,
                                               String view,
                                               String sortMode,
                                               Integer slaWindowMinutes,
                                               String pageSize) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("view", view);
        payload.put("sort_mode", sortMode);
        payload.put("page_size", pageSize);
        if (slaWindowMinutes != null) payload.put("sla_window_minutes", slaWindowMinutes);
        return uiPreferenceService.saveDialogsTriageForUser(authentication, payload);
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
