package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class DialogWorkspaceRequestContractService {

    private static final int DEFAULT_WORKSPACE_LIMIT = 50;
    private static final int MAX_WORKSPACE_LIMIT = 200;
    private static final Set<String> WORKSPACE_INCLUDE_ALLOWED = Set.of("messages", "context", "sla", "permissions");

    public void putProfileMatchField(Map<String, String> target, String key, Object value) {
        if (target == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        String normalized = String.valueOf(value).trim();
        if (!StringUtils.hasText(normalized) || "—".equals(normalized) || "-".equals(normalized)) {
            return;
        }
        target.put(key, normalized);
    }

    public int resolveDialogConfigRangeMinutes(Map<String, Object> settings,
                                               String key,
                                               int fallbackValue,
                                               int min,
                                               int max) {
        if (settings == null || settings.isEmpty()) {
            return fallbackValue;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return fallbackValue;
        }
        Object value = dialogConfig.get(key);
        if (value == null) {
            return fallbackValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed < min || parsed > max) {
                return fallbackValue;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return fallbackValue;
        }
    }

    public Set<String> resolveWorkspaceInclude(String include) {
        if (include == null || include.isBlank()) {
            return WORKSPACE_INCLUDE_ALLOWED;
        }
        Set<String> result = new HashSet<>();
        Arrays.stream(include.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(WORKSPACE_INCLUDE_ALLOWED::contains)
                .forEach(result::add);
        return result.isEmpty() ? WORKSPACE_INCLUDE_ALLOWED : Collections.unmodifiableSet(result);
    }

    public int resolveWorkspaceLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_WORKSPACE_LIMIT;
        }
        return Math.min(limit, MAX_WORKSPACE_LIMIT);
    }

    public int resolveWorkspaceCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
