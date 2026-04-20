package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogWorkspaceContextBlockService {

    public List<Map<String, Object>> buildContextBlocks(Map<String, Object> settings,
                                                        Map<String, Object> profileHealth,
                                                        List<Map<String, Object>> contextSources,
                                                        List<Map<String, Object>> clientHistory,
                                                        List<Map<String, Object>> relatedEvents,
                                                        String slaState,
                                                        Map<String, Object> externalLinks) {
        if (!isWorkspaceContextBlocksEnabled(settings)) {
            return List.of();
        }
        List<String> priority = resolveWorkspaceContextBlockPriority(settings);
        List<String> requiredBlocks = resolveWorkspaceRequiredContextBlocks(settings);
        Instant checkedAt = Instant.now();

        Map<String, Map<String, Object>> blockIndex = new LinkedHashMap<>();
        blockIndex.put("customer_profile", buildContextBlock(
                "customer_profile",
                "Минимальный профиль клиента",
                requiredBlocks.contains("customer_profile"),
                !toBoolean(profileHealth != null ? profileHealth.get("enabled") : null) || toBoolean(profileHealth.get("ready")),
                resolveWorkspaceContextProfileBlockStatus(profileHealth),
                resolveWorkspaceContextProfileBlockSummary(profileHealth),
                normalizeUtcTimestamp(profileHealth != null ? profileHealth.get("checked_at") : null),
                checkedAt
        ));
        blockIndex.put("context_sources", buildContextBlock(
                "context_sources",
                "Источники customer context",
                requiredBlocks.contains("context_sources"),
                resolveWorkspaceContextSourcesReady(contextSources),
                resolveWorkspaceContextSourcesBlockStatus(contextSources),
                resolveWorkspaceContextSourcesBlockSummary(contextSources),
                resolveWorkspaceContextSourcesUpdatedAtUtc(contextSources),
                checkedAt
        ));
        blockIndex.put("history", buildContextBlock(
                "history",
                "История обращений клиента",
                requiredBlocks.contains("history"),
                clientHistory != null,
                clientHistory != null ? "ready" : "missing",
                clientHistory == null
                        ? "История обращений недоступна."
                        : "Записей: %d.".formatted(clientHistory.size()),
                null,
                checkedAt
        ));
        blockIndex.put("related_events", buildContextBlock(
                "related_events",
                "Связанные события",
                requiredBlocks.contains("related_events"),
                relatedEvents != null,
                relatedEvents != null ? "ready" : "missing",
                relatedEvents == null
                        ? "Связанные события недоступны."
                        : "Событий: %d.".formatted(relatedEvents.size()),
                null,
                checkedAt
        ));
        blockIndex.put("sla", buildContextBlock(
                "sla",
                "SLA-контекст",
                requiredBlocks.contains("sla"),
                StringUtils.hasText(slaState) && !"unknown".equalsIgnoreCase(slaState),
                StringUtils.hasText(slaState) && !"unknown".equalsIgnoreCase(slaState) ? "ready" : "missing",
                StringUtils.hasText(slaState) && !"unknown".equalsIgnoreCase(slaState)
                        ? "SLA state: %s.".formatted(slaState)
                        : "SLA-контекст недоступен.",
                null,
                checkedAt
        ));
        blockIndex.put("external_links", buildContextBlock(
                "external_links",
                "Внешние customer links",
                requiredBlocks.contains("external_links"),
                externalLinks != null && !externalLinks.isEmpty(),
                externalLinks != null && !externalLinks.isEmpty() ? "ready" : "unavailable",
                externalLinks != null && !externalLinks.isEmpty()
                        ? "Ссылок: %d.".formatted(externalLinks.size())
                        : "Внешние ссылки не настроены.",
                null,
                checkedAt
        ));

        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String blockKey : priority) {
            Map<String, Object> block = blockIndex.remove(blockKey);
            if (block != null) {
                ordered.add(block);
            }
        }
        ordered.addAll(blockIndex.values());
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).put("priority", i + 1);
        }
        return ordered;
    }

    public Map<String, Object> buildBlocksHealth(List<Map<String, Object>> contextBlocks) {
        if (contextBlocks == null || contextBlocks.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> requiredBlocks = contextBlocks.stream()
                .filter(item -> toBoolean(item.get("required")))
                .toList();
        List<String> missingKeys = requiredBlocks.stream()
                .filter(item -> !toBoolean(item.get("ready")))
                .map(item -> String.valueOf(item.get("key")))
                .toList();
        List<String> missingLabels = requiredBlocks.stream()
                .filter(item -> !toBoolean(item.get("ready")))
                .map(item -> String.valueOf(item.get("label")))
                .toList();
        int coveragePct = requiredBlocks.isEmpty()
                ? 100
                : (int) Math.round(((requiredBlocks.size() - missingKeys.size()) * 100d) / requiredBlocks.size());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", true);
        payload.put("ready", missingKeys.isEmpty());
        payload.put("coverage_pct", Math.max(0, Math.min(100, coveragePct)));
        payload.put("required_count", requiredBlocks.size());
        payload.put("missing_required_keys", missingKeys);
        payload.put("missing_required_labels", missingLabels);
        payload.put("checked_at_utc", Instant.now().toString());
        return payload;
    }

    private Map<String, Object> buildContextBlock(String key,
                                                  String label,
                                                  boolean required,
                                                  boolean ready,
                                                  String status,
                                                  String summary,
                                                  String updatedAtUtc,
                                                  Instant checkedAt) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("key", key);
        block.put("label", label);
        block.put("required", required);
        block.put("ready", ready);
        block.put("status", normalizeWorkspaceContextBlockStatus(status));
        block.put("summary", trimToNull(summary));
        block.put("checked_at_utc", checkedAt != null ? checkedAt.toString() : Instant.now().toString());
        if (StringUtils.hasText(updatedAtUtc)) {
            block.put("updated_at_utc", updatedAtUtc);
        }
        return block;
    }

    private boolean isWorkspaceContextBlocksEnabled(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return false;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return false;
        }
        return dialogConfig.containsKey("workspace_context_block_priority")
                || dialogConfig.containsKey("workspace_context_block_required");
    }

    private List<String> resolveWorkspaceContextBlockPriority(Map<String, Object> settings) {
        List<String> defaults = new ArrayList<>(List.of(
                "customer_profile",
                "context_sources",
                "history",
                "related_events",
                "sla",
                "external_links"
        ));
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return defaults;
        }
        Object priorityRaw = dialogConfig.get("workspace_context_block_priority");
        if (!(priorityRaw instanceof List<?> priorityList)) {
            return defaults;
        }
        List<String> ordered = new ArrayList<>();
        priorityList.forEach(item -> appendNormalizedContextSource(ordered, item));
        defaults.forEach(item -> {
            if (!ordered.contains(item)) {
                ordered.add(item);
            }
        });
        return ordered;
    }

    private List<String> resolveWorkspaceRequiredContextBlocks(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return List.of();
        }
        Object requiredRaw = dialogConfig.get("workspace_context_block_required");
        List<String> required = new ArrayList<>();
        if (requiredRaw instanceof List<?> requiredList) {
            requiredList.forEach(item -> appendNormalizedContextSource(required, item));
            return required;
        }
        if (requiredRaw != null) {
            for (String part : String.valueOf(requiredRaw).split(",")) {
                appendNormalizedContextSource(required, part);
            }
        }
        return required;
    }

    private String normalizeWorkspaceContextBlockStatus(String status) {
        String normalized = trimToNull(status);
        if (!StringUtils.hasText(normalized)) {
            return "unavailable";
        }
        return switch (normalized.toLowerCase()) {
            case "ready", "missing", "attention", "invalid_utc", "stale", "unavailable" -> normalized.toLowerCase();
            default -> "attention";
        };
    }

    private String resolveWorkspaceContextProfileBlockStatus(Map<String, Object> profileHealth) {
        if (profileHealth == null || profileHealth.isEmpty() || !toBoolean(profileHealth.get("enabled"))) {
            return "ready";
        }
        return toBoolean(profileHealth.get("ready")) ? "ready" : "attention";
    }

    private String resolveWorkspaceContextProfileBlockSummary(Map<String, Object> profileHealth) {
        if (profileHealth == null || profileHealth.isEmpty() || !toBoolean(profileHealth.get("enabled"))) {
            return "Минимальный customer profile не требует дополнительных полей.";
        }
        List<String> missingLabels = safeStringList(profileHealth.get("missing_field_labels"));
        if (toBoolean(profileHealth.get("ready"))) {
            return "Обязательные поля customer profile заполнены.";
        }
        return "Не хватает полей: %s.".formatted(String.join(", ", missingLabels.isEmpty() ? List.of("нет данных") : missingLabels));
    }

    private boolean resolveWorkspaceContextSourcesReady(List<Map<String, Object>> contextSources) {
        if (contextSources == null || contextSources.isEmpty()) {
            return true;
        }
        return contextSources.stream()
                .filter(item -> toBoolean(item.get("required")))
                .allMatch(item -> toBoolean(item.get("ready")));
    }

    private String resolveWorkspaceContextSourcesBlockStatus(List<Map<String, Object>> contextSources) {
        if (contextSources == null || contextSources.isEmpty()) {
            return "unavailable";
        }
        return contextSources.stream()
                .filter(item -> toBoolean(item.get("required")))
                .filter(item -> !toBoolean(item.get("ready")))
                .map(item -> normalizeWorkspaceContextBlockStatus(String.valueOf(item.get("status"))))
                .findFirst()
                .orElse("ready");
    }

    private String resolveWorkspaceContextSourcesBlockSummary(List<Map<String, Object>> contextSources) {
        if (contextSources == null || contextSources.isEmpty()) {
            return "Источники customer context не настроены.";
        }
        List<String> blocking = contextSources.stream()
                .filter(item -> toBoolean(item.get("required")))
                .filter(item -> !toBoolean(item.get("ready")))
                .map(item -> "%s (%s)".formatted(item.get("label"), item.get("status")))
                .toList();
        if (blocking.isEmpty()) {
            return "Все обязательные источники customer context готовы.";
        }
        return "Проблемные источники: %s.".formatted(String.join(", ", blocking));
    }

    private String resolveWorkspaceContextSourcesUpdatedAtUtc(List<Map<String, Object>> contextSources) {
        if (contextSources == null || contextSources.isEmpty()) {
            return null;
        }
        return contextSources.stream()
                .map(item -> normalizeUtcTimestamp(item.get("updated_at_utc")))
                .filter(StringUtils::hasText)
                .max(String::compareTo)
                .orElse(null);
    }

    private void appendNormalizedContextSource(List<String> target, Object rawValue) {
        String normalized = normalizeMacroVariableKey(String.valueOf(rawValue));
        if (StringUtils.hasText(normalized) && !target.contains(normalized)) {
            target.add(normalized);
        }
    }

    private List<String> safeStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(item -> trimToNull(String.valueOf(item)))
                .filter(StringUtils::hasText)
                .toList();
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = value != null ? String.valueOf(value).trim().toLowerCase() : "";
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return switch (normalized) {
            case "1", "true", "yes", "on", "ok", "ready" -> true;
            default -> false;
        };
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

    private OffsetDateTime parseUtcTimestamp(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fallback to legacy datetime-local without explicit offset
        }
        try {
            return LocalDateTime.parse(rawValue).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String normalizeUtcTimestamp(Object rawValue) {
        String normalized = trimToNull(rawValue != null ? String.valueOf(rawValue) : null);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        OffsetDateTime parsed = parseUtcTimestamp(normalized);
        return parsed != null ? parsed.toString() : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
