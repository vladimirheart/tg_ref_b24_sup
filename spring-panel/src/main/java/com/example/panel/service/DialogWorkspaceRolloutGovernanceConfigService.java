package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogWorkspaceRolloutGovernanceConfigService {

    private final SharedConfigService sharedConfigService;

    public DialogWorkspaceRolloutGovernanceConfigService(SharedConfigService sharedConfigService) {
        this.sharedConfigService = sharedConfigService;
    }

    public DialogWorkspaceRolloutGovernanceConfig loadConfig() {
        boolean packetRequired = resolveBooleanDialogConfigValue("workspace_rollout_governance_packet_required", false);
        boolean ownerSignoffRequired = resolveBooleanDialogConfigValue("workspace_rollout_governance_owner_signoff_required", false);
        String ownerSignoffBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_owner_signoff_by")));
        String ownerSignoffAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_owner_signoff_at"));
        long ownerSignoffTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_governance_owner_signoff_ttl_hours", 168, 1, 24 * 90L);
        long reviewCadenceDays = resolveLongDialogConfigValue(
                "workspace_rollout_governance_review_cadence_days", 0, 0, 90);
        String reviewCadenceBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_reviewed_by")));
        String reviewCadenceAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_reviewed_at"));
        String reviewCadenceNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_review_note")));
        String reviewDecisionAction = normalizeDecisionAction(
                normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_review_decision_action"))),
                List.of("go", "hold", "rollback"));
        String reviewIncidentFollowup = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_review_incident_followup")));
        List<String> reviewRequiredCriteria = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_review_required_criteria"));
        List<String> reviewCheckedCriteria = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_review_checked_criteria"));
        boolean reviewDecisionRequired = resolveBooleanDialogConfigValue("workspace_rollout_governance_review_decision_required", false);
        boolean reviewIncidentFollowupRequired = resolveBooleanDialogConfigValue("workspace_rollout_governance_incident_followup_required", false);
        boolean reviewFollowupForNonGoRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_governance_followup_for_non_go_required", false);
        String previousDecisionAction = normalizeDecisionAction(
                normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_previous_decision_action"))),
                List.of("go", "hold", "rollback"));
        String previousDecisionAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_previous_decision_at"));
        long parityExitDays = resolveLongDialogConfigValue(
                "workspace_rollout_governance_parity_exit_days", 0, 0, 90);
        List<String> parityCriticalReasons = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_parity_critical_reasons"));
        List<String> legacyOnlyScenarios = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_legacy_only_scenarios"));
        Map<String, Map<String, Object>> legacyOnlyScenarioMetadata = resolveLegacyOnlyScenarioMetadataMap(
                resolveDialogConfigValue("workspace_rollout_governance_legacy_only_scenario_metadata"));
        String legacyInventoryReviewedBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_inventory_reviewed_by")));
        String legacyInventoryReviewedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_inventory_reviewed_at"));
        String legacyInventoryReviewNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_inventory_review_note")));
        String legacyUsageReviewedBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_usage_reviewed_by")));
        String legacyUsageReviewedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_usage_reviewed_at"));
        String legacyUsageReviewNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_usage_review_note")));
        String legacyUsageDecision = normalizeDecisionAction(
                normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_usage_decision"))),
                List.of("go", "hold"));
        long legacyUsageReviewTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_review_ttl_hours", 168, 1, 24 * 90L);
        Long legacyUsageMaxSharePct = resolveNullableLongDialogConfigValue(
                "workspace_rollout_governance_legacy_manual_share_max_pct", 0, 100);
        Long legacyUsageMinWorkspaceOpenEvents = resolveNullableLongDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_min_workspace_open_events", 0, 100_000);
        Long legacyUsageMaxShareDeltaPct = resolveNullableLongDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_max_share_delta_pct", 0, 100);
        Long legacyUsageMaxBlockedShareDeltaPct = resolveNullableLongDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_max_blocked_share_delta_pct", 0, 100);
        List<String> legacyManualAllowedReasons = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_legacy_manual_open_allowed_reasons"));
        boolean legacyManualReasonCatalogRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_legacy_manual_open_reason_catalog_required", false);
        boolean legacyBlockedReasonsReviewRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_governance_legacy_blocked_reasons_review_required", false);
        long legacyBlockedReasonsTopN = resolveLongDialogConfigValue(
                "workspace_rollout_governance_legacy_blocked_reasons_top_n", 3, 1, 10);
        List<String> legacyBlockedReasonsReviewed = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_legacy_blocked_reasons_reviewed"));
        String legacyBlockedReasonsFollowup = normalizeNullString(
                String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_blocked_reasons_followup")));
        boolean legacyUsageDecisionRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_decision_required", false);
        boolean contextContractRequired = resolveBooleanDialogConfigValue("workspace_rollout_context_contract_required", false);
        List<String> contextContractScenarios = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_context_contract_scenarios"));
        List<String> contextContractMandatoryFields = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_context_contract_mandatory_fields"));
        Map<String, List<String>> contextContractMandatoryFieldsByScenario = resolveDialogConfigStringListMap(
                resolveDialogConfigValue("workspace_rollout_context_contract_mandatory_fields_by_scenario"));
        List<String> contextContractSourceOfTruth = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_context_contract_source_of_truth"));
        Map<String, List<String>> contextContractSourceOfTruthByScenario = resolveDialogConfigStringListMap(
                resolveDialogConfigValue("workspace_rollout_context_contract_source_of_truth_by_scenario"));
        List<String> contextContractPriorityBlocks = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_context_contract_priority_blocks"));
        Map<String, List<String>> contextContractPriorityBlocksByScenario = resolveDialogConfigStringListMap(
                resolveDialogConfigValue("workspace_rollout_context_contract_priority_blocks_by_scenario"));
        Map<String, Map<String, String>> contextContractPlaybooks = resolveContextContractPlaybooks(
                resolveDialogConfigValue("workspace_rollout_context_contract_playbooks"));
        String contextContractReviewedBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_context_contract_reviewed_by")));
        String contextContractReviewedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_context_contract_reviewed_at"));
        String contextContractReviewNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_context_contract_review_note")));
        long contextContractReviewTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_context_contract_review_ttl_hours", 168, 1, 24 * 90L);

        return new DialogWorkspaceRolloutGovernanceConfig(
                packetRequired,
                ownerSignoffRequired,
                ownerSignoffBy,
                ownerSignoffAtRaw,
                ownerSignoffTtlHours,
                reviewCadenceDays,
                reviewCadenceBy,
                reviewCadenceAtRaw,
                reviewCadenceNote,
                reviewDecisionAction,
                reviewIncidentFollowup,
                reviewRequiredCriteria,
                reviewCheckedCriteria,
                reviewDecisionRequired,
                reviewIncidentFollowupRequired,
                reviewFollowupForNonGoRequired,
                previousDecisionAction,
                previousDecisionAtRaw,
                parityExitDays,
                parityCriticalReasons,
                legacyOnlyScenarios,
                legacyOnlyScenarioMetadata,
                legacyInventoryReviewedBy,
                legacyInventoryReviewedAtRaw,
                legacyInventoryReviewNote,
                legacyUsageReviewedBy,
                legacyUsageReviewedAtRaw,
                legacyUsageReviewNote,
                legacyUsageDecision,
                legacyUsageReviewTtlHours,
                legacyUsageMaxSharePct,
                legacyUsageMinWorkspaceOpenEvents,
                legacyUsageMaxShareDeltaPct,
                legacyUsageMaxBlockedShareDeltaPct,
                legacyManualAllowedReasons,
                legacyManualReasonCatalogRequired,
                legacyBlockedReasonsReviewRequired,
                legacyBlockedReasonsTopN,
                legacyBlockedReasonsReviewed,
                legacyBlockedReasonsFollowup,
                legacyUsageDecisionRequired,
                contextContractRequired,
                contextContractScenarios,
                contextContractMandatoryFields,
                contextContractMandatoryFieldsByScenario,
                contextContractSourceOfTruth,
                contextContractSourceOfTruthByScenario,
                contextContractPriorityBlocks,
                contextContractPriorityBlocksByScenario,
                contextContractPlaybooks,
                contextContractReviewedBy,
                contextContractReviewedAtRaw,
                contextContractReviewNote,
                contextContractReviewTtlHours
        );
    }

    public String normalizeUtcTimestamp(Object rawValue) {
        OffsetDateTime parsed = parseReviewTimestamp(rawValue == null ? null : String.valueOf(rawValue));
        return parsed != null ? parsed.withOffsetSameInstant(ZoneOffset.UTC).toString() : "";
    }

    public String normalizeNullString(String value) {
        if (value == null || "null".equalsIgnoreCase(value)) {
            return "";
        }
        return value.trim();
    }

    public OffsetDateTime parseReviewTimestamp(String rawValue) {
        String value = normalizeNullString(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // fallback below
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // fallback below
        }
        try {
            return Timestamp.valueOf(value).toInstant().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    public Map<String, Object> castObjectMap(Map<?, ?> source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        source.forEach((key, value) -> payload.put(String.valueOf(key), value));
        return payload;
    }

    public boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String normalized = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    public long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    public double safeDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0d : Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return 0d;
        }
    }

    public String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    public List<Map<String, Object>> safeListOfMaps(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(castObjectMap(map));
            }
        }
        return result;
    }

    public List<String> buildContextContractPlaybookExpectedKeys(DialogWorkspaceRolloutGovernanceConfig config) {
        return buildContextContractPlaybookExpectedKeys(
                config.contextContractMandatoryFields(),
                config.contextContractMandatoryFieldsByScenario(),
                config.contextContractSourceOfTruth(),
                config.contextContractSourceOfTruthByScenario(),
                config.contextContractPriorityBlocks(),
                config.contextContractPriorityBlocksByScenario());
    }

    public boolean hasContextContractPlaybookCoverage(Map<String, Map<String, String>> playbooks, String key) {
        if (!StringUtils.hasText(key)) {
            return true;
        }
        if (playbooks == null || playbooks.isEmpty()) {
            return false;
        }
        String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
        if (playbooks.keySet().stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalizedKey::equals)) {
            return true;
        }
        int separatorIndex = normalizedKey.indexOf(':');
        if (separatorIndex <= 0) {
            return false;
        }
        String genericTypeKey = normalizedKey.substring(0, separatorIndex);
        return playbooks.keySet().stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(genericTypeKey::equals);
    }

    private String normalizeDecisionAction(String value, List<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : null;
    }

    private List<String> resolveDialogConfigStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(item -> item != null)
                    .map(String::valueOf)
                    .map(this::normalizeNullString)
                    .filter(StringUtils::hasText)
                    .map(item -> item.toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
        }
        String text = normalizeNullString(value == null ? null : String.valueOf(value));
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return Arrays.stream(text.split("[,\\n]"))
                .map(this::normalizeNullString)
                .filter(StringUtils::hasText)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private Map<String, List<String>> resolveDialogConfigStringListMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalizeNullString(entry.getKey() == null ? null : String.valueOf(entry.getKey()))
                    .toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(key)) {
                continue;
            }
            List<String> items = resolveDialogConfigStringList(entry.getValue());
            if (!items.isEmpty()) {
                normalized.put(key, items);
            }
        }
        return normalized;
    }

    private Map<String, Map<String, Object>> resolveLegacyOnlyScenarioMetadataMap(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String scenario = normalizeNullString(entry.getKey() == null ? null : String.valueOf(entry.getKey()))
                    .toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(scenario) || !(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            String owner = normalizeNullString(String.valueOf(item.get("owner")));
            String deadline = normalizeNullString(String.valueOf(item.get("deadline_at_utc")));
            boolean deadlineTimestampInvalid = StringUtils.hasText(deadline) && parseReviewTimestamp(deadline) == null;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("owner", owner);
            payload.put("deadline_at_utc", deadline);
            payload.put("deadline_timestamp_invalid", deadlineTimestampInvalid);
            payload.put("note", normalizeNullString(String.valueOf(item.get("note"))));
            normalized.put(scenario, payload);
        }
        return normalized;
    }

    private Map<String, Map<String, String>> resolveContextContractPlaybooks(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = normalizeNullString(entry.getKey() == null ? null : String.valueOf(entry.getKey()))
                    .toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(key) || !(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            String label = normalizeNullString(String.valueOf(item.get("label")));
            String url = normalizeNullString(String.valueOf(item.get("url")));
            String owner = normalizeNullString(String.valueOf(item.get("owner")));
            String summary = normalizeNullString(String.valueOf(item.get("summary")));
            if (!StringUtils.hasText(url)
                    || (!url.startsWith("https://") && !url.startsWith("http://"))
                    || (!StringUtils.hasText(owner) && !StringUtils.hasText(summary) && !StringUtils.hasText(label))) {
                continue;
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("label", label == null ? "" : label);
            payload.put("url", url);
            payload.put("owner", owner == null ? "" : owner);
            payload.put("summary", summary == null ? "" : summary);
            normalized.put(key, payload);
        }
        return normalized;
    }

    private List<String> buildContextContractPlaybookExpectedKeys(List<String> mandatoryFields,
                                                                  Map<String, List<String>> mandatoryFieldsByScenario,
                                                                  List<String> sourceOfTruth,
                                                                  Map<String, List<String>> sourceOfTruthByScenario,
                                                                  List<String> priorityBlocks,
                                                                  Map<String, List<String>> priorityBlocksByScenario) {
        List<String> result = new ArrayList<>();
        mandatoryFields.forEach(field -> result.add("mandatory_field:" + field));
        mandatoryFieldsByScenario.forEach((scenario, fields) -> fields.forEach(field -> result.add("mandatory_field:" + scenario + ":" + field)));
        sourceOfTruth.forEach(rule -> result.add(normalizeContextContractPlaybookScopedSourceKey(rule)));
        sourceOfTruthByScenario.forEach((scenario, rules) -> rules.forEach(rule ->
                result.add("source_of_truth:" + scenario + ":" + normalizeNullString(rule).toLowerCase(Locale.ROOT))));
        priorityBlocks.forEach(block -> result.add("priority_block:" + block));
        priorityBlocksByScenario.forEach((scenario, blocks) -> blocks.forEach(block -> result.add("priority_block:" + scenario + ":" + block)));
        return result.stream()
                .map(value -> value == null ? null : value.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String normalizeContextContractPlaybookScopedSourceKey(String sourceRule) {
        String value = normalizeNullString(sourceRule);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("source_of_truth:") ? normalized : "source_of_truth:" + normalized;
    }

    private long resolveLongDialogConfigValue(String key, long fallback, long minInclusive, long maxInclusive) {
        Object value = resolveDialogConfigValue(key);
        if (value == null) {
            return fallback;
        }
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return parsed < minInclusive || parsed > maxInclusive ? fallback : parsed;
    }

    private Long resolveNullableLongDialogConfigValue(String key, long minInclusive, long maxInclusive) {
        Object value = resolveDialogConfigValue(key);
        if (value == null) {
            return null;
        }
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return parsed < minInclusive || parsed > maxInclusive ? null : parsed;
    }

    private boolean resolveBooleanDialogConfigValue(String key, boolean fallback) {
        Object value = resolveDialogConfigValue(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return fallback;
        }
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private Object resolveDialogConfigValue(String key) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        if (settings == null || settings.isEmpty()) {
            return null;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> map)) {
            return null;
        }
        return map.get(key);
    }
}
