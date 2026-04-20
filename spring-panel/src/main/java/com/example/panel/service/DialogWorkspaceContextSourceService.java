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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DialogWorkspaceContextSourceService {

    private final DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService;

    public DialogWorkspaceContextSourceService(DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService) {
        this.dialogWorkspaceClientProfileService = dialogWorkspaceClientProfileService;
    }

    public List<Map<String, Object>> buildContextSources(Map<String, Object> settings,
                                                         Map<String, Object> workspaceClient,
                                                         Map<String, Object> localProfileEnrichment,
                                                         Map<String, Object> externalProfileEnrichment,
                                                         Map<String, Object> externalLinks) {
        if (!isWorkspaceContextSourcesEnabled(settings)) {
            return List.of();
        }
        List<String> requiredSources = resolveWorkspaceRequiredContextSources(settings);
        List<String> configuredOrder = resolveWorkspaceContextSourcePriority(settings);
        Map<String, String> configuredLabels = resolveWorkspaceContextSourceLabels(settings);
        Map<String, List<String>> configuredUpdatedAtAttributes = resolveWorkspaceContextSourceUpdatedAtAttributes(settings);
        int defaultStaleAfterHours = resolveWorkspaceContextSourceStaleAfterHours(settings);
        Map<String, Integer> sourceSpecificStaleAfterHours = resolveWorkspaceContextSourceStaleAfterHoursBySource(settings, defaultStaleAfterHours);

        LinkedHashSet<String> sourceKeys = new LinkedHashSet<>();
        sourceKeys.add("local");
        configuredOrder.forEach(sourceKeys::add);
        requiredSources.forEach(sourceKeys::add);
        if (hasWorkspaceSourceCoverage("crm", workspaceClient, localProfileEnrichment, externalProfileEnrichment, externalLinks)) {
            sourceKeys.add("crm");
        }
        if (hasWorkspaceSourceCoverage("contract", workspaceClient, localProfileEnrichment, externalProfileEnrichment, externalLinks)) {
            sourceKeys.add("contract");
        }
        if (hasWorkspaceSourceCoverage("external", workspaceClient, localProfileEnrichment, externalProfileEnrichment, externalLinks)) {
            sourceKeys.add("external");
        }

        List<Map<String, Object>> sources = new ArrayList<>();
        Instant now = Instant.now();
        for (String sourceKey : sourceKeys) {
            String normalizedKey = normalizeMacroVariableKey(sourceKey);
            if (!StringUtils.hasText(normalizedKey)) {
                continue;
            }
            boolean required = requiredSources.contains(normalizedKey);
            List<String> matchedAttributes = resolveWorkspaceContextSourceMatchedAttributes(
                    normalizedKey,
                    workspaceClient,
                    localProfileEnrichment,
                    externalProfileEnrichment
            );
            boolean linked = externalLinks != null && externalLinks.containsKey(normalizedKey);
            boolean available = "local".equals(normalizedKey) || !matchedAttributes.isEmpty() || linked;
            int staleAfterHours = sourceSpecificStaleAfterHours.getOrDefault(normalizedKey, defaultStaleAfterHours);

            TimestampResolution timestampResolution = resolveWorkspaceContextSourceTimestamp(
                    normalizedKey,
                    workspaceClient,
                    configuredUpdatedAtAttributes
            );
            boolean stale = available
                    && timestampResolution.updatedAtUtc() != null
                    && staleAfterHours > 0
                    && timestampResolution.updatedAtUtc().toInstant().isBefore(now.minusSeconds(staleAfterHours * 3600L));

            List<String> issues = new ArrayList<>();
            String status;
            if (!available) {
                status = required ? "missing" : "unavailable";
                if (required) {
                    issues.add("missing");
                }
            } else if (timestampResolution.invalidUtc()) {
                status = "invalid_utc";
                issues.add("invalid_utc");
            } else if (stale) {
                status = "stale";
                issues.add("stale");
            } else {
                status = "ready";
            }

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("key", normalizedKey);
            source.put("label", configuredLabels.getOrDefault(normalizedKey, humanizeLabel(normalizedKey)));
            source.put("required", required);
            source.put("status", status);
            source.put("ready", "ready".equals(status));
            source.put("available", available);
            source.put("linked", linked);
            source.put("matched_attributes", matchedAttributes);
            source.put("matched_attribute_count", matchedAttributes.size());
            source.put("freshness_ttl_hours", staleAfterHours > 0 ? staleAfterHours : null);
            source.put("freshness_policy_scope", sourceSpecificStaleAfterHours.containsKey(normalizedKey) ? "source" : "global");
            source.put("issues", issues);
            if (timestampResolution.attributeKey() != null) {
                source.put("updated_at_attribute", timestampResolution.attributeKey());
            }
            if (timestampResolution.updatedAtRaw() != null) {
                source.put("updated_at_raw", timestampResolution.updatedAtRaw());
            }
            if (timestampResolution.updatedAtUtc() != null) {
                source.put("updated_at_utc", timestampResolution.updatedAtUtc().toString());
            }
            source.put("summary", buildWorkspaceContextSourceSummary(
                    status,
                    required,
                    linked,
                    matchedAttributes.size(),
                    timestampResolution,
                    staleAfterHours
            ));
            sources.add(source);
        }
        return sources;
    }

    public List<Map<String, Object>> buildContextAttributePolicies(Map<String, Object> workspaceClient,
                                                                   Map<String, Object> profileHealth,
                                                                   List<Map<String, Object>> contextSources) {
        if (workspaceClient == null || workspaceClient.isEmpty()
                || profileHealth == null || profileHealth.isEmpty()
                || !toBoolean(profileHealth.get("enabled"))) {
            return List.of();
        }
        List<String> requiredFields = safeStringList(profileHealth.get("required_fields"));
        if (requiredFields.isEmpty()) {
            return List.of();
        }
        Map<String, String> attributeLabels = workspaceClient.get("attribute_labels") instanceof Map<?, ?> labels
                ? labels.entrySet().stream()
                .filter(entry -> StringUtils.hasText(normalizeMacroVariableKey(String.valueOf(entry.getKey()))))
                .collect(Collectors.toMap(
                        entry -> normalizeMacroVariableKey(String.valueOf(entry.getKey())),
                        entry -> String.valueOf(entry.getValue()),
                        (first, second) -> first,
                        LinkedHashMap::new))
                : Map.of();
        List<Map<String, Object>> safeSources = contextSources == null ? List.of() : contextSources;

        List<Map<String, Object>> policies = new ArrayList<>();
        for (String field : requiredFields) {
            String normalizedField = normalizeMacroVariableKey(field);
            if (!StringUtils.hasText(normalizedField)) {
                continue;
            }
            Object value = workspaceClient.get(normalizedField);
            boolean valueReady = dialogWorkspaceClientProfileService.hasProfileValue(value);
            Map<String, Object> preferredSource = safeSources.stream()
                    .filter(source -> safeStringList(source.get("matched_attributes")).contains(normalizedField))
                    .findFirst()
                    .orElse(null);
            String status;
            List<String> issues = new ArrayList<>();
            if (!valueReady) {
                status = "missing";
                issues.add("missing");
            } else if (preferredSource == null) {
                status = "untracked";
                issues.add("untracked");
            } else {
                status = normalizeWorkspaceAttributePolicyStatus(String.valueOf(preferredSource.get("status")));
                if (!"ready".equals(status)) {
                    issues.add(status);
                }
            }

            Map<String, Object> policy = new LinkedHashMap<>();
            policy.put("key", normalizedField);
            policy.put("label", attributeLabels.getOrDefault(normalizedField, humanizeLabel(normalizedField)));
            policy.put("required", true);
            policy.put("ready", "ready".equals(status));
            policy.put("status", status);
            policy.put("issues", issues);
            if (preferredSource != null) {
                policy.put("source_key", preferredSource.get("key"));
                policy.put("source_label", preferredSource.get("label"));
                policy.put("source_ready", toBoolean(preferredSource.get("ready")));
                policy.put("freshness_required", preferredSource.get("freshness_ttl_hours") instanceof Number);
                if (preferredSource.get("freshness_ttl_hours") instanceof Number ttl) {
                    policy.put("freshness_ttl_hours", ttl.intValue());
                }
                if (preferredSource.get("updated_at_utc") != null) {
                    policy.put("updated_at_utc", preferredSource.get("updated_at_utc"));
                }
                if (preferredSource.get("updated_at_raw") != null) {
                    policy.put("updated_at_raw", preferredSource.get("updated_at_raw"));
                }
            } else {
                policy.put("freshness_required", false);
            }
            policy.put("summary", buildWorkspaceContextAttributePolicySummary(
                    normalizedField,
                    status,
                    valueReady,
                    preferredSource
            ));
            policies.add(policy);
        }
        return policies;
    }

    private boolean isWorkspaceContextSourcesEnabled(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return false;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return false;
        }
        return dialogConfig.containsKey("workspace_client_context_required_sources")
                || dialogConfig.containsKey("workspace_client_context_source_labels")
                || dialogConfig.containsKey("workspace_client_context_source_priority")
                || dialogConfig.containsKey("workspace_client_context_source_updated_at_attributes")
                || dialogConfig.containsKey("workspace_client_context_source_stale_after_hours")
                || dialogConfig.containsKey("workspace_client_context_source_stale_after_hours_by_source");
    }

    private boolean hasWorkspaceSourceCoverage(String sourceKey,
                                               Map<String, Object> workspaceClient,
                                               Map<String, Object> localProfileEnrichment,
                                               Map<String, Object> externalProfileEnrichment,
                                               Map<String, Object> externalLinks) {
        if ("local".equals(sourceKey)) {
            return true;
        }
        if (externalLinks != null && externalLinks.containsKey(sourceKey)) {
            return true;
        }
        return !resolveWorkspaceContextSourceMatchedAttributes(sourceKey, workspaceClient, localProfileEnrichment, externalProfileEnrichment).isEmpty();
    }

    private List<String> resolveWorkspaceContextSourceMatchedAttributes(String sourceKey,
                                                                       Map<String, Object> workspaceClient,
                                                                       Map<String, Object> localProfileEnrichment,
                                                                       Map<String, Object> externalProfileEnrichment) {
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        if ("local".equals(sourceKey)) {
            if (workspaceClient != null) {
                List.of("name", "status", "last_message_at", "responsible").forEach(field -> {
                    if (dialogWorkspaceClientProfileService.hasProfileValue(workspaceClient.get(field))) {
                        matches.add(field);
                    }
                });
            }
            if (localProfileEnrichment != null) {
                localProfileEnrichment.keySet().forEach(key -> {
                    String normalized = normalizeMacroVariableKey(key);
                    if (StringUtils.hasText(normalized)) {
                        matches.add(normalized);
                    }
                });
            }
            return new ArrayList<>(matches);
        }

        String prefix = sourceKey + "_";
        appendMatchingWorkspaceSourceKeys(matches, workspaceClient, prefix);
        appendMatchingWorkspaceSourceKeys(matches, localProfileEnrichment, prefix);
        appendMatchingWorkspaceSourceKeys(matches, externalProfileEnrichment, prefix);
        return new ArrayList<>(matches);
    }

    private void appendMatchingWorkspaceSourceKeys(Set<String> matches,
                                                   Map<String, Object> values,
                                                   String prefix) {
        if (values == null || values.isEmpty()) {
            return;
        }
        values.forEach((rawKey, value) -> {
            String normalized = normalizeMacroVariableKey(String.valueOf(rawKey));
            if (StringUtils.hasText(normalized) && normalized.startsWith(prefix) && dialogWorkspaceClientProfileService.hasProfileValue(value)) {
                matches.add(normalized);
            }
        });
    }

    private Map<String, String> resolveWorkspaceContextSourceLabels(Map<String, Object> settings) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("local", "Локальный профиль");
        labels.put("crm", "CRM");
        labels.put("contract", "Контракт");
        labels.put("external", "Внешний источник");
        if (settings == null || settings.isEmpty()) {
            return labels;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return labels;
        }
        Object labelsRaw = dialogConfig.get("workspace_client_context_source_labels");
        if (!(labelsRaw instanceof Map<?, ?> labelMap)) {
            return labels;
        }
        labelMap.forEach((keyRaw, valueRaw) -> {
            String key = normalizeMacroVariableKey(String.valueOf(keyRaw));
            String value = trimToNull(String.valueOf(valueRaw));
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                labels.put(key, value);
            }
        });
        return labels;
    }

    private List<String> resolveWorkspaceRequiredContextSources(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return List.of();
        }
        Object requiredRaw = dialogConfig.get("workspace_client_context_required_sources");
        List<String> required = new ArrayList<>();
        if (requiredRaw instanceof List<?> items) {
            items.forEach(item -> appendNormalizedContextSource(required, item));
            return required;
        }
        if (requiredRaw != null) {
            for (String part : String.valueOf(requiredRaw).split(",")) {
                appendNormalizedContextSource(required, part);
            }
        }
        return required;
    }

    private List<String> resolveWorkspaceContextSourcePriority(Map<String, Object> settings) {
        List<String> defaults = new ArrayList<>(List.of("local", "crm", "contract", "external"));
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return defaults;
        }
        Object priorityRaw = dialogConfig.get("workspace_client_context_source_priority");
        if (!(priorityRaw instanceof List<?> priorityList)) {
            return defaults;
        }
        List<String> ordered = new ArrayList<>();
        priorityList.forEach(item -> appendNormalizedContextSource(ordered, item));
        defaults.forEach(defaultItem -> {
            if (!ordered.contains(defaultItem)) {
                ordered.add(defaultItem);
            }
        });
        return ordered;
    }

    private Map<String, List<String>> resolveWorkspaceContextSourceUpdatedAtAttributes(Map<String, Object> settings) {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("local", List.of("local_updated_at", "last_ticket_activity_at", "last_message_at"));
        defaults.put("crm", List.of("crm_updated_at", "crm_profile_updated_at"));
        defaults.put("contract", List.of("contract_updated_at", "contract_profile_updated_at"));
        defaults.put("external", List.of("external_updated_at", "profile_updated_at", "source_updated_at"));
        if (settings == null || settings.isEmpty()) {
            return defaults;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return defaults;
        }
        Object updatedAtRaw = dialogConfig.get("workspace_client_context_source_updated_at_attributes");
        if (!(updatedAtRaw instanceof Map<?, ?> updatedAtMap)) {
            return defaults;
        }
        updatedAtMap.forEach((keyRaw, valueRaw) -> {
            String sourceKey = normalizeMacroVariableKey(String.valueOf(keyRaw));
            if (!StringUtils.hasText(sourceKey)) {
                return;
            }
            List<String> attributes = new ArrayList<>();
            if (valueRaw instanceof List<?> items) {
                items.forEach(item -> appendNormalizedContextSource(attributes, item));
            } else if (valueRaw != null) {
                for (String part : String.valueOf(valueRaw).split(",")) {
                    appendNormalizedContextSource(attributes, part);
                }
            }
            if (!attributes.isEmpty()) {
                defaults.put(sourceKey, attributes);
            }
        });
        return defaults;
    }

    private int resolveWorkspaceContextSourceStaleAfterHours(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return 0;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return 0;
        }
        return resolveIntegerDialogConfigValue(dialogConfig.get("workspace_client_context_source_stale_after_hours"), 0, 0, 24 * 365);
    }

    private Map<String, Integer> resolveWorkspaceContextSourceStaleAfterHoursBySource(Map<String, Object> settings,
                                                                                       int fallbackValue) {
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Object rawValue = dialogConfig.get("workspace_client_context_source_stale_after_hours_by_source");
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        rawMap.forEach((sourceRaw, ttlRaw) -> {
            String sourceKey = normalizeMacroVariableKey(String.valueOf(sourceRaw));
            if (!StringUtils.hasText(sourceKey)) {
                return;
            }
            normalized.put(sourceKey, resolveIntegerDialogConfigValue(ttlRaw, fallbackValue, 0, 24 * 365));
        });
        return normalized;
    }

    private TimestampResolution resolveWorkspaceContextSourceTimestamp(String sourceKey,
                                                                      Map<String, Object> workspaceClient,
                                                                      Map<String, List<String>> configuredUpdatedAtAttributes) {
        List<String> candidateAttributes = configuredUpdatedAtAttributes.getOrDefault(sourceKey, List.of());
        for (String attributeKey : candidateAttributes) {
            Object rawValue = workspaceClient != null ? workspaceClient.get(attributeKey) : null;
            String normalizedRawValue = trimToNull(String.valueOf(rawValue));
            if (!StringUtils.hasText(normalizedRawValue)) {
                continue;
            }
            OffsetDateTime parsed = parseUtcTimestamp(normalizedRawValue);
            return new TimestampResolution(attributeKey, normalizedRawValue, parsed, parsed == null);
        }
        return new TimestampResolution(null, null, null, false);
    }

    private String buildWorkspaceContextSourceSummary(String status,
                                                      boolean required,
                                                      boolean linked,
                                                      int matchedAttributeCount,
                                                      TimestampResolution timestampResolution,
                                                      int staleAfterHours) {
        return switch (StringUtils.hasText(status) ? status : "unavailable") {
            case "ready" -> "Источник готов: %d атрибутов%s%s.".formatted(
                    matchedAttributeCount,
                    linked ? ", есть внешняя ссылка" : "",
                    timestampResolution.updatedAtUtc() != null ? ", UTC " + timestampResolution.updatedAtUtc() : ""
            );
            case "stale" -> "Источник доступен, но устарел по TTL %dч.".formatted(Math.max(0, staleAfterHours));
            case "invalid_utc" -> "Дата источника не распознана как UTC: %s.".formatted(
                    timestampResolution.updatedAtRaw() != null ? timestampResolution.updatedAtRaw() : "пусто"
            );
            case "missing" -> required
                    ? "Обязательный источник ещё не подключён или не вернул атрибуты."
                    : "Источник пока недоступен.";
            default -> linked
                    ? "Источник доступен только как внешняя ссылка."
                    : "Источник не предоставил данных.";
        };
    }

    private String normalizeWorkspaceAttributePolicyStatus(String status) {
        String normalized = trimToNull(status);
        if (!StringUtils.hasText(normalized)) {
            return "untracked";
        }
        return switch (normalized.toLowerCase()) {
            case "ready", "stale", "invalid_utc", "missing", "untracked", "unavailable" -> normalized.toLowerCase();
            default -> "untracked";
        };
    }

    private String buildWorkspaceContextAttributePolicySummary(String field,
                                                               String status,
                                                               boolean valueReady,
                                                               Map<String, Object> preferredSource) {
        if (!valueReady) {
            return "Поле %s не заполнено в mandatory profile.".formatted(field);
        }
        if (preferredSource == null) {
            return "Для поля %s не определён source/freshness policy.".formatted(field);
        }
        String sourceLabel = String.valueOf(preferredSource.getOrDefault("label", preferredSource.getOrDefault("key", "source")));
        return switch (status) {
            case "ready" -> "Поле %s подтверждено источником %s.".formatted(field, sourceLabel);
            case "stale" -> "Поле %s опирается на stale-источник %s.".formatted(field, sourceLabel);
            case "invalid_utc" -> "Для поля %s источник %s вернул невалидный UTC timestamp.".formatted(field, sourceLabel);
            case "missing" -> "Для поля %s обязательный источник %s недоступен.".formatted(field, sourceLabel);
            case "unavailable" -> "Для поля %s источник %s пока недоступен.".formatted(field, sourceLabel);
            default -> "Для поля %s policy по источнику %s ещё не формализована.".formatted(field, sourceLabel);
        };
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

    private int resolveIntegerDialogConfigValue(Object rawValue, int fallbackValue, int min, int max) {
        if (rawValue == null) {
            return fallbackValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ex) {
            return fallbackValue;
        }
    }

    private OffsetDateTime parseUtcTimestamp(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(rawValue).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
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

    private String humanizeLabel(String key) {
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

    private record TimestampResolution(String attributeKey,
                                       String updatedAtRaw,
                                       OffsetDateTime updatedAtUtc,
                                       boolean invalidUtc) {
    }
}
