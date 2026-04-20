package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class DialogWorkspaceClientProfileService {

    public List<String> buildClientSegments(DialogListItem summary,
                                            Map<String, Object> profileEnrichment,
                                            Map<String, Object> settings) {
        if (summary == null) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        if (summary.unreadCount() != null && summary.unreadCount() > 0) {
            segments.add("needs_reply");
        }
        if (summary.responsible() == null || summary.responsible().isBlank()) {
            segments.add("unassigned");
        }
        if (summary.rating() != null && summary.rating() > 0 && summary.rating() <= 2) {
            segments.add("low_csat_risk");
        }
        if ("new".equals(summary.statusKey())) {
            segments.add("new_dialog");
        }

        int totalDialogs = parseInteger(profileEnrichment != null ? profileEnrichment.get("total_dialogs") : null);
        int openDialogs = parseInteger(profileEnrichment != null ? profileEnrichment.get("open_dialogs") : null);
        int resolved30d = parseInteger(profileEnrichment != null ? profileEnrichment.get("resolved_30d") : null);

        int highLifetimeDialogsThreshold = resolveDialogConfigRangeValue(
                settings,
                "workspace_segment_high_lifetime_volume_min_dialogs",
                5,
                1,
                500
        );
        int multiOpenDialogsThreshold = resolveDialogConfigRangeValue(
                settings,
                "workspace_segment_multi_open_dialogs_min_open",
                2,
                1,
                50
        );
        int reactivationDialogsThreshold = resolveDialogConfigRangeValue(
                settings,
                "workspace_segment_reactivation_risk_min_dialogs",
                3,
                1,
                500
        );
        int reactivationResolvedThreshold = resolveDialogConfigRangeValue(
                settings,
                "workspace_segment_reactivation_risk_max_resolved_30d",
                0,
                0,
                100
        );
        int openBacklogMinOpenThreshold = resolveDialogConfigRangeValue(
                settings,
                "workspace_segment_open_backlog_min_open",
                3,
                1,
                100
        );
        int openBacklogMinSharePercent = resolveDialogConfigRangeValue(
                settings,
                "workspace_segment_open_backlog_min_share_percent",
                50,
                1,
                100
        );

        if (totalDialogs >= highLifetimeDialogsThreshold) {
            segments.add("high_lifetime_volume");
        }
        if (openDialogs >= multiOpenDialogsThreshold) {
            segments.add("multi_open_dialogs");
        }
        if (totalDialogs >= reactivationDialogsThreshold && resolved30d <= reactivationResolvedThreshold) {
            segments.add("reactivation_risk");
        }
        if (openDialogs >= openBacklogMinOpenThreshold && totalDialogs > 0) {
            int openSharePercent = Math.round((openDialogs * 100f) / totalDialogs);
            if (openSharePercent >= openBacklogMinSharePercent) {
                segments.add("open_backlog_pressure");
            }
        }
        return segments;
    }

    public Map<String, Object> buildProfileHealth(Map<String, Object> settings,
                                                  Map<String, Object> workspaceClient,
                                                  Map<String, String> configuredLabels) {
        List<String> globalRequiredFields = resolveWorkspaceRequiredClientAttributes(settings);
        Map<String, List<String>> segmentRequiredFields = resolveWorkspaceRequiredClientAttributesBySegment(settings);
        List<String> activeSegments = resolveWorkspaceClientSegments(workspaceClient);
        List<String> requiredFields = mergeWorkspaceRequiredClientAttributes(globalRequiredFields, segmentRequiredFields, activeSegments);
        Instant checkedAt = Instant.now();
        if (requiredFields.isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enabled", false);
            payload.put("ready", true);
            payload.put("coverage_pct", 100);
            payload.put("checked_at", checkedAt.toString());
            payload.put("checked_at_utc", checkedAt.toString());
            payload.put("required_fields", List.of());
            payload.put("global_required_fields", List.of());
            payload.put("segment_required_fields", Map.of());
            payload.put("active_segments", activeSegments);
            payload.put("missing_fields", List.of());
            payload.put("missing_field_labels", List.of());
            return payload;
        }
        Map<String, String> labelMap = new LinkedHashMap<>();
        if (configuredLabels != null) {
            configuredLabels.forEach((key, value) -> {
                String normalizedKey = normalizeMacroVariableKey(key);
                if (StringUtils.hasText(normalizedKey) && StringUtils.hasText(value)) {
                    labelMap.put(normalizedKey, value);
                }
            });
        }
        labelMap.putIfAbsent("id", "ID");
        labelMap.putIfAbsent("name", "Имя");
        labelMap.putIfAbsent("username", "Username");
        labelMap.putIfAbsent("status", "Статус");
        labelMap.putIfAbsent("channel", "Канал");
        labelMap.putIfAbsent("business", "Бизнес");
        labelMap.putIfAbsent("location", "Локация");
        labelMap.putIfAbsent("responsible", "Ответственный");
        labelMap.putIfAbsent("last_message_at", "Последнее сообщение");
        labelMap.putIfAbsent("first_seen_at", "Первое обращение");
        labelMap.putIfAbsent("last_ticket_activity_at", "Последняя активность тикета");

        List<String> missingFields = new ArrayList<>();
        List<String> missingFieldLabels = new ArrayList<>();
        for (String field : requiredFields) {
            Object value = workspaceClient != null ? workspaceClient.get(field) : null;
            if (hasWorkspaceProfileValue(value)) {
                continue;
            }
            missingFields.add(field);
            missingFieldLabels.add(labelMap.getOrDefault(field, humanizeMacroVariableLabel(field)));
        }

        int totalRequired = requiredFields.size();
        int readyCount = totalRequired - missingFields.size();
        int coveragePct = totalRequired > 0
                ? (int) Math.round((readyCount * 100d) / totalRequired)
                : 100;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", true);
        payload.put("ready", missingFields.isEmpty());
        payload.put("coverage_pct", Math.max(0, Math.min(100, coveragePct)));
        payload.put("checked_at", checkedAt.toString());
        payload.put("checked_at_utc", checkedAt.toString());
        payload.put("required_fields", requiredFields);
        payload.put("global_required_fields", globalRequiredFields);
        payload.put("segment_required_fields", segmentRequiredFields);
        payload.put("active_segments", activeSegments);
        payload.put("missing_fields", missingFields);
        payload.put("missing_field_labels", missingFieldLabels);
        return payload;
    }

    public boolean hasProfileValue(Object value) {
        return hasWorkspaceProfileValue(value);
    }

    private List<String> resolveWorkspaceClientSegments(Map<String, Object> workspaceClient) {
        if (workspaceClient == null || workspaceClient.isEmpty()) {
            return List.of();
        }
        Object segmentsRaw = workspaceClient.get("segments");
        if (!(segmentsRaw instanceof List<?> segments)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        segments.forEach(item -> appendNormalizedValue(normalized, item));
        return normalized;
    }

    private Map<String, List<String>> resolveWorkspaceRequiredClientAttributesBySegment(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Object rawValue = dialogConfig.get("workspace_required_client_attributes_by_segment");
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        rawMap.forEach((segmentRaw, attributesRaw) -> {
            String segmentKey = normalizeMacroVariableKey(String.valueOf(segmentRaw));
            if (!StringUtils.hasText(segmentKey)) {
                return;
            }
            List<String> attributes = normalizeWorkspaceClientAttributeList(attributesRaw);
            if (!attributes.isEmpty()) {
                normalized.put(segmentKey, attributes);
            }
        });
        return normalized;
    }

    private List<String> mergeWorkspaceRequiredClientAttributes(List<String> globalRequiredFields,
                                                                Map<String, List<String>> segmentRequiredFields,
                                                                List<String> activeSegments) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (globalRequiredFields != null) {
            merged.addAll(globalRequiredFields);
        }
        if (segmentRequiredFields != null && !segmentRequiredFields.isEmpty() && activeSegments != null) {
            activeSegments.forEach(segment -> merged.addAll(segmentRequiredFields.getOrDefault(segment, List.of())));
        }
        return new ArrayList<>(merged);
    }

    private List<String> normalizeWorkspaceClientAttributeList(Object rawValue) {
        List<String> normalized = new ArrayList<>();
        if (rawValue instanceof List<?> values) {
            values.forEach(item -> appendNormalizedValue(normalized, item));
            return normalized;
        }
        if (rawValue != null) {
            for (String part : String.valueOf(rawValue).split(",")) {
                appendNormalizedValue(normalized, part);
            }
        }
        return normalized;
    }

    private boolean hasWorkspaceProfileValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Map<?, ?> mapValue) {
            return !mapValue.isEmpty();
        }
        if (value instanceof List<?> listValue) {
            return !listValue.isEmpty();
        }
        return StringUtils.hasText(String.valueOf(value));
    }

    private List<String> resolveWorkspaceRequiredClientAttributes(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return List.of();
        }
        Object requiredRaw = dialogConfig.get("workspace_required_client_attributes");
        List<String> normalized = new ArrayList<>();
        if (requiredRaw instanceof List<?> values) {
            values.forEach(item -> appendNormalizedValue(normalized, item));
            return normalized;
        }
        if (requiredRaw != null) {
            for (String part : String.valueOf(requiredRaw).split(",")) {
                appendNormalizedValue(normalized, part);
            }
        }
        return normalized;
    }

    private int resolveDialogConfigRangeValue(Map<String, Object> settings,
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

    private int parseInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void appendNormalizedValue(List<String> target, Object rawValue) {
        String normalized = normalizeMacroVariableKey(String.valueOf(rawValue));
        if (StringUtils.hasText(normalized) && !target.contains(normalized)) {
            target.add(normalized);
        }
    }

    private String normalizeMacroVariableKey(String rawValue) {
        String value = trimToNull(rawValue);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private String humanizeMacroVariableLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return "Переменная";
        }
        String[] parts = key.trim().toLowerCase().split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            words.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return String.join(" ", words);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
