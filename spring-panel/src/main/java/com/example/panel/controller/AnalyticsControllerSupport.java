package com.example.panel.controller;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;

final class AnalyticsControllerSupport {

    private AnalyticsControllerSupport() {
    }

    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    static String resolveActor(Authentication authentication, String fallback) {
        if (authentication != null && normalize(authentication.getName()) != null) {
            return authentication.getName();
        }
        if (normalize(fallback) != null) {
            return fallback;
        }
        return "anonymous";
    }

    static OffsetDateTime parseUtcTimestamp(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(value);
            if (!ZoneOffset.UTC.equals(parsed.getOffset())) {
                return null;
            }
            return parsed.withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                Instant parsed = Instant.parse(value);
                return value.endsWith("Z") ? parsed.atOffset(ZoneOffset.UTC) : null;
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    static long parsePositiveLong(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(String.valueOf(value).trim()));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    static List<String> sanitizeStringList(Object raw) {
        Stream<?> stream;
        if (raw instanceof List<?> list) {
            stream = list.stream();
        } else if (raw instanceof String value) {
            stream = Stream.of(value.split("[,\\n;]"));
        } else {
            return List.of();
        }
        return stream
                .map(item -> normalize(item == null ? null : String.valueOf(item)))
                .filter(value -> value != null && value.length() <= 120)
                .distinct()
                .toList();
    }

    static Map<String, List<String>> sanitizeStringListMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalize(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (key == null || key.length() > 120) {
                continue;
            }
            List<String> values = sanitizeStringList(entry.getValue());
            if (!values.isEmpty()) {
                result.put(key.toLowerCase(Locale.ROOT), values);
            }
        }
        return result;
    }

    static Map<String, ContextPlaybookPayload> sanitizeContextPlaybookMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, ContextPlaybookPayload> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalize(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (key == null || key.length() > 160) {
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            String label = truncate(normalize(item.get("label") == null ? null : String.valueOf(item.get("label"))), 160);
            String url = normalize(item.get("url") == null ? null : String.valueOf(item.get("url")));
            String summary = truncate(normalize(item.get("summary") == null ? null : String.valueOf(item.get("summary"))), 300);
            if (url == null || (!url.startsWith("https://") && !url.startsWith("http://"))) {
                continue;
            }
            result.put(key.toLowerCase(Locale.ROOT), new ContextPlaybookPayload(
                    label == null ? "Playbook" : label,
                    url,
                    summary));
        }
        return result;
    }

    static Map<String, LegacyScenarioMetadataPayload> sanitizeLegacyScenarioMetadataMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, LegacyScenarioMetadataPayload> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String scenario = normalize(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (scenario == null || scenario.length() > 120 || !(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            String owner = truncate(normalize(item.get("owner") == null ? null : String.valueOf(item.get("owner"))), 120);
            String deadlineRaw = normalize(item.get("deadlineAtUtc") == null ? null : String.valueOf(item.get("deadlineAtUtc")));
            if (deadlineRaw == null) {
                deadlineRaw = normalize(item.get("deadline_at_utc") == null ? null : String.valueOf(item.get("deadline_at_utc")));
            }
            String deadlineAtUtc = null;
            if (deadlineRaw != null) {
                OffsetDateTime parsed = parseUtcTimestamp(deadlineRaw);
                if (parsed == null) {
                    throw new IllegalArgumentException("legacy scenario deadline_at_utc must be a valid UTC timestamp (ISO-8601)");
                }
                deadlineAtUtc = parsed.toInstant().toString();
            }
            String note = truncate(normalize(item.get("note") == null ? null : String.valueOf(item.get("note"))), 300);
            if (owner != null || deadlineAtUtc != null || note != null) {
                result.put(scenario.toLowerCase(Locale.ROOT), new LegacyScenarioMetadataPayload(owner, deadlineAtUtc, note));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mutableDialogConfig(Map<String, Object> settings) {
        return settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    record WorkspaceRolloutReviewRequest(String reviewedBy,
                                         String reviewedAtUtc,
                                         String reviewNote,
                                         String decisionAction,
                                         String incidentFollowup,
                                         Object requiredCriteria,
                                         Object checkedCriteria) {
    }

    record WorkspaceContextStandardRequest(Boolean required,
                                           Object scenarios,
                                           Object mandatoryFields,
                                           Object scenarioMandatoryFields,
                                           Object sourceOfTruth,
                                           Object scenarioSourceOfTruth,
                                           Object priorityBlocks,
                                           Object scenarioPriorityBlocks,
                                           Object playbooks,
                                           String reviewedBy,
                                           String reviewedAtUtc,
                                           String note) {
    }

    record WorkspaceLegacyOnlyScenariosRequest(Object scenarios,
                                               Object scenarioMetadata,
                                               String reviewedBy,
                                               String reviewedAtUtc,
                                               String note) {
    }

    record WorkspaceLegacyUsagePolicyRequest(String reviewedBy,
                                             String reviewedAtUtc,
                                             String reviewNote,
                                             String decision,
                                             Long maxLegacyManualSharePct,
                                             Long minWorkspaceOpenEvents,
                                             Long maxLegacyManualShareDeltaPct,
                                             Long maxLegacyBlockedShareDeltaPct,
                                             Object allowedReasons,
                                             Boolean reasonCatalogRequired,
                                             Object blockedReasonsReviewed,
                                             String blockedReasonsFollowup) {
    }

    record SlaPolicyGovernanceReviewRequest(String reviewedBy,
                                            String reviewedAtUtc,
                                            String reviewNote,
                                            String dryRunTicketId,
                                            String decision,
                                            String policyChangedAtUtc) {
    }

    record MacroGovernanceReviewRequest(String reviewedBy,
                                        String reviewedAtUtc,
                                        String reviewNote,
                                        String cleanupTicketId,
                                        String decision) {
    }

    record MacroExternalCatalogPolicyRequest(String verifiedBy,
                                             String verifiedAtUtc,
                                             String expectedVersion,
                                             String observedVersion,
                                             String reviewNote,
                                             String decision,
                                             Long reviewTtlHours) {
    }

    record MacroDeprecationPolicyRequest(String reviewedBy,
                                         String reviewedAtUtc,
                                         String deprecationTicketId,
                                         String reviewNote,
                                         String decision,
                                         Long reviewTtlHours) {
    }

    record ContextPlaybookPayload(String label,
                                  String url,
                                  String summary) {
    }

    record LegacyScenarioMetadataPayload(String owner,
                                         String deadlineAtUtc,
                                         String note) {
    }
}
