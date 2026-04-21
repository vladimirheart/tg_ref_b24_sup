package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class DialogWorkspaceContextContractService {

    private final DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService;

    public DialogWorkspaceContextContractService(DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService) {
        this.dialogWorkspaceClientProfileService = dialogWorkspaceClientProfileService;
    }

    public Map<String, Object> buildContextContract(Map<String, Object> settings,
                                                    DialogListItem summary,
                                                    Map<String, Object> workspaceClient,
                                                    List<Map<String, Object>> contextSources,
                                                    List<Map<String, Object>> contextBlocks) {
        Map<String, Object> dialogConfig = extractDialogConfig(settings);
        if (dialogConfig.isEmpty()) {
            return Map.of("enabled", false, "required", false, "ready", true);
        }
        boolean required = toBoolean(dialogConfig.get("workspace_rollout_context_contract_required"));
        List<String> scenarios = safeStringList(dialogConfig.get("workspace_rollout_context_contract_scenarios"));
        List<String> mandatoryFields = safeStringList(dialogConfig.get("workspace_rollout_context_contract_mandatory_fields"));
        Map<String, List<String>> mandatoryFieldsByScenario = safeStringListMap(
                dialogConfig.get("workspace_rollout_context_contract_mandatory_fields_by_scenario"));
        List<String> sourceOfTruth = safeStringList(dialogConfig.get("workspace_rollout_context_contract_source_of_truth"));
        Map<String, List<String>> sourceOfTruthByScenario = safeStringListMap(
                dialogConfig.get("workspace_rollout_context_contract_source_of_truth_by_scenario"));
        List<String> priorityBlocks = safeStringList(dialogConfig.get("workspace_rollout_context_contract_priority_blocks"));
        Map<String, List<String>> priorityBlocksByScenario = safeStringListMap(
                dialogConfig.get("workspace_rollout_context_contract_priority_blocks_by_scenario"));
        Map<String, Map<String, String>> playbooks = resolveContextContractPlaybooks(dialogConfig);
        boolean enabled = required
                || !scenarios.isEmpty()
                || !mandatoryFields.isEmpty()
                || !mandatoryFieldsByScenario.isEmpty()
                || !sourceOfTruth.isEmpty()
                || !sourceOfTruthByScenario.isEmpty()
                || !priorityBlocks.isEmpty()
                || !priorityBlocksByScenario.isEmpty()
                || !playbooks.isEmpty();
        if (!enabled) {
            return Map.of("enabled", false, "required", false, "ready", true);
        }

        List<Map<String, Object>> safeSources = contextSources == null ? List.of() : contextSources;
        List<Map<String, Object>> safeBlocks = contextBlocks == null ? List.of() : contextBlocks;
        List<String> activeScenarios = resolveActiveScenarios(summary, scenarios);
        List<String> effectiveMandatoryFields = computeEffectiveScenarioList(
                mandatoryFields,
                mandatoryFieldsByScenario,
                activeScenarios);
        List<String> effectiveSourceOfTruth = computeEffectiveScenarioList(
                sourceOfTruth,
                sourceOfTruthByScenario,
                activeScenarios);
        List<String> effectivePriorityBlocks = computeEffectiveScenarioList(
                priorityBlocks,
                priorityBlocksByScenario,
                activeScenarios);
        List<String> missingMandatoryFields = effectiveMandatoryFields.stream()
                .map(this::normalizeMacroVariableKey)
                .filter(StringUtils::hasText)
                .filter(field -> !dialogWorkspaceClientProfileService.hasProfileValue(workspaceClient.get(field)))
                .distinct()
                .toList();

        List<String> sourceViolations = effectiveSourceOfTruth.stream()
                .map(rule -> resolveContextSourceViolation(rule, safeSources))
                .filter(StringUtils::hasText)
                .toList();

        List<String> missingPriorityBlocks = effectivePriorityBlocks.stream()
                .map(this::normalizeMacroVariableKey)
                .filter(StringUtils::hasText)
                .filter(requiredBlock -> safeBlocks.stream().noneMatch(block ->
                        requiredBlock.equals(normalizeMacroVariableKey(String.valueOf(block.get("key"))))
                                && toBoolean(block.get("ready"))))
                .toList();

        List<String> violations = Stream.of(
                        missingMandatoryFields.stream().map(field -> "mandatory_field:" + field),
                        sourceViolations.stream().map(reason -> "source_of_truth:" + reason),
                        missingPriorityBlocks.stream().map(block -> "priority_block:" + block))
                .flatMap(stream -> stream)
                .distinct()
                .toList();
        List<Map<String, Object>> violationDetails = buildWorkspaceContextContractViolationDetails(
                workspaceClient,
                safeSources,
                safeBlocks,
                missingMandatoryFields,
                sourceViolations,
                missingPriorityBlocks,
                playbooks);
        List<Map<String, Object>> primaryViolationDetails = violationDetails.stream()
                .limit(2)
                .toList();
        int deferredViolationCount = Math.max(0, violationDetails.size() - primaryViolationDetails.size());
        List<String> definitionGaps = Stream.of(
                        scenarios.isEmpty() ? "scenarios" : null,
                        (mandatoryFields.isEmpty() && mandatoryFieldsByScenario.isEmpty()) ? "mandatory_fields" : null,
                        (sourceOfTruth.isEmpty() && sourceOfTruthByScenario.isEmpty()) ? "source_of_truth" : null,
                        (priorityBlocks.isEmpty() && priorityBlocksByScenario.isEmpty()) ? "priority_blocks" : null)
                .filter(StringUtils::hasText)
                .toList();
        List<String> operatorFocusBlocks = Stream.concat(priorityBlocks.stream(), priorityBlocksByScenario.values().stream().flatMap(List::stream))
                .map(this::normalizeMacroVariableKey)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();
        List<String> actionItems = new ArrayList<>();
        if (!definitionGaps.isEmpty()) {
            actionItems.add("Дополните contract definitions: " + String.join(", ", definitionGaps) + ".");
        }
        if (!missingMandatoryFields.isEmpty()) {
            actionItems.add("Сначала дозаполните поля: " + String.join(", ", missingMandatoryFields.stream().limit(3).toList()) + ".");
        } else if (!sourceViolations.isEmpty()) {
            actionItems.add("Проверьте источники данных: " + String.join(", ", sourceViolations.stream().limit(2).toList()) + ".");
        } else if (!missingPriorityBlocks.isEmpty()) {
            actionItems.add("Верните в workspace блоки: " + String.join(", ", missingPriorityBlocks.stream().limit(3).toList()) + ".");
        }
        String operatorSummary = violations.isEmpty()
                ? "Minimum profile соблюдён."
                : !missingMandatoryFields.isEmpty()
                ? "Сначала заполните обязательные поля клиента."
                : !sourceViolations.isEmpty()
                ? "Проверьте source-of-truth и freshness для customer context."
                : !missingPriorityBlocks.isEmpty()
                ? "Верните обязательные context-блоки в основной workspace."
                : !definitionGaps.isEmpty()
                ? "Contract definitions требуют cleanup."
                : "Context contract требует action-oriented follow-up.";
        String nextStepSummary = actionItems.isEmpty() ? "" : actionItems.get(0);
        boolean definitionReady = !scenarios.isEmpty()
                && (!mandatoryFields.isEmpty() || !mandatoryFieldsByScenario.isEmpty())
                && (!sourceOfTruth.isEmpty() || !sourceOfTruthByScenario.isEmpty())
                && (!priorityBlocks.isEmpty() || !priorityBlocksByScenario.isEmpty());
        boolean ready = violations.isEmpty() && definitionReady;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", true);
        payload.put("required", required);
        payload.put("ready", ready);
        payload.put("definition_ready", definitionReady);
        payload.put("scenarios", scenarios);
        payload.put("mandatory_fields", mandatoryFields);
        payload.put("mandatory_fields_by_scenario", mandatoryFieldsByScenario);
        payload.put("active_scenarios", activeScenarios);
        payload.put("effective_mandatory_fields", effectiveMandatoryFields);
        payload.put("source_of_truth", sourceOfTruth);
        payload.put("source_of_truth_by_scenario", sourceOfTruthByScenario);
        payload.put("effective_source_of_truth", effectiveSourceOfTruth);
        payload.put("priority_blocks", priorityBlocks);
        payload.put("priority_blocks_by_scenario", priorityBlocksByScenario);
        payload.put("effective_priority_blocks", effectivePriorityBlocks);
        payload.put("missing_mandatory_fields", missingMandatoryFields);
        payload.put("source_of_truth_violations", sourceViolations);
        payload.put("missing_priority_blocks", missingPriorityBlocks);
        payload.put("violations", violations);
        payload.put("violation_details", violationDetails);
        payload.put("primary_violation_details", primaryViolationDetails);
        payload.put("deferred_violation_count", deferredViolationCount);
        payload.put("playbooks", playbooks);
        payload.put("definition_gaps", definitionGaps);
        payload.put("operator_focus_blocks", operatorFocusBlocks);
        payload.put("progressive_disclosure_ready", !operatorFocusBlocks.isEmpty());
        payload.put("operator_summary", operatorSummary);
        payload.put("next_step_summary", nextStepSummary);
        payload.put("action_items", actionItems);
        payload.put("checked_at_utc", Instant.now().toString());
        return payload;
    }

    private List<String> resolveActiveScenarios(DialogListItem summary, List<String> configuredScenarios) {
        if (configuredScenarios.isEmpty()) {
            return List.of();
        }
        String category = summary.categoriesSafe();
        List<String> active = new ArrayList<>();
        for (String scenario : configuredScenarios) {
            if (StringUtils.hasText(category) && category.toLowerCase(Locale.ROOT).contains(scenario.toLowerCase(Locale.ROOT))) {
                active.add(scenario);
            }
        }
        return active.isEmpty() && !configuredScenarios.isEmpty() ? List.of(configuredScenarios.get(0)) : active;
    }

    private List<String> computeEffectiveScenarioList(List<String> baseline,
                                                      Map<String, List<String>> byScenario,
                                                      List<String> activeScenarios) {
        Set<String> effective = new LinkedHashSet<>(baseline);
        for (String scenario : activeScenarios) {
            List<String> scenarioFields = byScenario.get(scenario.toLowerCase(Locale.ROOT));
            if (scenarioFields != null) {
                effective.addAll(scenarioFields);
            }
        }
        return new ArrayList<>(effective);
    }

    private String resolveContextSourceViolation(String sourceRule, List<Map<String, Object>> contextSources) {
        String normalizedRule = trimToNull(sourceRule);
        if (normalizedRule == null) {
            return null;
        }
        String[] parts = normalizedRule.split(":", 2);
        String field = normalizeMacroVariableKey(parts[0]);
        String source = parts.length > 1 ? normalizeMacroVariableKey(parts[1]) : null;
        if (!StringUtils.hasText(field) || !StringUtils.hasText(source)) {
            return normalizedRule + ":invalid_rule";
        }
        Map<String, Object> matchedSource = contextSources.stream()
                .filter(item -> source.equals(normalizeMacroVariableKey(String.valueOf(item.get("key")))))
                .findFirst()
                .orElse(null);
        if (matchedSource == null) {
            return field + ":" + source + ":source_missing";
        }
        if (!safeStringList(matchedSource.get("matched_attributes")).contains(field)) {
            return field + ":" + source + ":field_not_matched";
        }
        if (!toBoolean(matchedSource.get("ready"))) {
            String status = normalizeMacroVariableKey(String.valueOf(matchedSource.get("status")));
            return field + ":" + source + ":" + (StringUtils.hasText(status) ? status : "not_ready");
        }
        return null;
    }

    private Map<String, Map<String, String>> resolveContextContractPlaybooks(Map<String, Object> dialogConfig) {
        Object raw = dialogConfig.get("workspace_rollout_context_contract_playbooks");
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalizeMacroVariableKey(String.valueOf(entry.getKey()));
            if (!StringUtils.hasText(key) || !(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            Object labelRaw = item.get("label");
            Object urlRaw = item.get("url");
            Object summaryRaw = item.get("summary");
            String label = trimToNull(labelRaw == null ? null : String.valueOf(labelRaw));
            String url = trimToNull(urlRaw == null ? null : String.valueOf(urlRaw));
            String summary = trimToNull(summaryRaw == null ? null : String.valueOf(summaryRaw));
            if (!StringUtils.hasText(url) || (!url.startsWith("https://") && !url.startsWith("http://"))) {
                continue;
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("label", label != null ? label : "Playbook");
            payload.put("url", url);
            payload.put("summary", summary != null ? summary : "");
            result.put(key, payload);
        }
        return result;
    }

    private List<Map<String, Object>> buildWorkspaceContextContractViolationDetails(Map<String, Object> workspaceClient,
                                                                                    List<Map<String, Object>> contextSources,
                                                                                    List<Map<String, Object>> contextBlocks,
                                                                                    List<String> missingMandatoryFields,
                                                                                    List<String> sourceViolations,
                                                                                    List<String> missingPriorityBlocks,
                                                                                    Map<String, Map<String, String>> playbooks) {
        List<Map<String, Object>> result = new ArrayList<>();
        missingMandatoryFields.forEach(field -> result.add(buildMandatoryFieldViolationDetail(field, workspaceClient, playbooks)));
        sourceViolations.forEach(violation -> result.add(buildSourceOfTruthViolationDetail(violation, workspaceClient, contextSources, playbooks)));
        missingPriorityBlocks.forEach(block -> result.add(buildPriorityBlockViolationDetail(block, contextBlocks, playbooks)));
        return result;
    }

    private Map<String, Object> buildMandatoryFieldViolationDetail(String field,
                                                                   Map<String, Object> workspaceClient,
                                                                   Map<String, Map<String, String>> playbooks) {
        String normalizedField = normalizeMacroVariableKey(field);
        String label = resolveWorkspaceContextAttributeLabel(workspaceClient, normalizedField);
        String code = "mandatory_field:" + normalizedField;
        return buildContextViolationDetail(
                code,
                "mandatory_field",
                normalizedField,
                "high",
                "Поле \"" + label + "\" не заполнено",
                "Заполните обязательное поле \"" + label + "\" в карточке клиента.",
                "Свяжитесь с клиентом или проверьте CRM, затем сохраните поле \"" + label + "\".",
                "Missing mandatory field: " + label,
                resolveContextContractPlaybook(playbooks, code, "mandatory_field:" + normalizedField, "mandatory_field"));
    }

    private Map<String, Object> buildSourceOfTruthViolationDetail(String rawViolation,
                                                                  Map<String, Object> workspaceClient,
                                                                  List<Map<String, Object>> contextSources,
                                                                  Map<String, Map<String, String>> playbooks) {
        String normalized = trimToNull(rawViolation);
        String[] parts = normalized == null ? new String[0] : normalized.split(":");
        String field = parts.length > 0 ? normalizeMacroVariableKey(parts[0]) : "";
        String source = parts.length > 1 ? normalizeMacroVariableKey(parts[1]) : "";
        String status = parts.length > 2 ? normalizeMacroVariableKey(parts[2]) : "not_ready";
        String fieldLabel = resolveWorkspaceContextAttributeLabel(workspaceClient, field);
        String sourceLabel = resolveWorkspaceContextSourceLabel(contextSources, source);
        String operatorMessage;
        String shortLabel;
        String nextStep;
        if ("source_missing".equals(status)) {
            shortLabel = "Нет источника \"" + sourceLabel + "\"";
            operatorMessage = "Подключите источник \"" + sourceLabel + "\" для поля \"" + fieldLabel + "\".";
            nextStep = "Проверьте интеграцию или включите источник \"" + sourceLabel + "\" для этого клиента.";
        } else if ("field_not_matched".equals(status)) {
            shortLabel = "Поле \"" + fieldLabel + "\" не приходит из \"" + sourceLabel + "\"";
            operatorMessage = "Проверьте, что источник \"" + sourceLabel + "\" действительно отдаёт поле \"" + fieldLabel + "\".";
            nextStep = "Сверьте маппинг поля \"" + fieldLabel + "\" и обновите источник \"" + sourceLabel + "\".";
        } else if ("stale".equals(status)) {
            shortLabel = "Данные \"" + fieldLabel + "\" устарели";
            operatorMessage = "Обновите данные \"" + fieldLabel + "\" из источника \"" + sourceLabel + "\".";
            nextStep = "Запустите обновление из \"" + sourceLabel + "\" или подтвердите актуальность данных вручную.";
        } else if ("invalid_utc".equals(status)) {
            shortLabel = "Невалидный UTC timestamp для \"" + sourceLabel + "\"";
            operatorMessage = "Исправьте UTC timestamp источника \"" + sourceLabel + "\" для поля \"" + fieldLabel + "\".";
            nextStep = "Исправьте timestamp источника \"" + sourceLabel + "\" в ISO-8601 UTC и повторите синхронизацию.";
        } else {
            shortLabel = "Проблема с источником \"" + sourceLabel + "\"";
            operatorMessage = "Проверьте источник \"" + sourceLabel + "\" для поля \"" + fieldLabel + "\".";
            nextStep = "Проверьте доступность источника \"" + sourceLabel + "\" и корректность данных для поля \"" + fieldLabel + "\".";
        }
        String code = "source_of_truth:" + normalized;
        return buildContextViolationDetail(
                code,
                "source_of_truth",
                field,
                "invalid_utc".equals(status) || "source_missing".equals(status) ? "high" : "medium",
                shortLabel,
                operatorMessage,
                nextStep,
                "Source-of-truth gap: " + fieldLabel + " via " + sourceLabel + " (" + status + ")",
                resolveContextContractPlaybook(playbooks, code, "source_of_truth:" + field + ":" + source, "source_of_truth"));
    }

    private Map<String, Object> buildPriorityBlockViolationDetail(String block,
                                                                  List<Map<String, Object>> contextBlocks,
                                                                  Map<String, Map<String, String>> playbooks) {
        String normalizedBlock = normalizeMacroVariableKey(block);
        String label = resolveWorkspaceContextBlockLabel(contextBlocks, normalizedBlock);
        String code = "priority_block:" + normalizedBlock;
        return buildContextViolationDetail(
                code,
                "priority_block",
                normalizedBlock,
                "medium",
                "Нет блока \"" + label + "\"",
                "Верните в рабочий контур блок \"" + label + "\" или снимите его из обязательных для этого сценария.",
                "Верните блок \"" + label + "\" в workspace либо обновите contract для текущего сценария.",
                "Priority block missing: " + label,
                resolveContextContractPlaybook(playbooks, code, "priority_block:" + normalizedBlock, "priority_block"));
    }

    private Map<String, Object> buildContextViolationDetail(String code,
                                                            String type,
                                                            String key,
                                                            String severity,
                                                            String shortLabel,
                                                            String operatorMessage,
                                                            String nextStep,
                                                            String analyticsMessage,
                                                            Map<String, String> playbook) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("type", type);
        payload.put("key", key);
        payload.put("severity", severity);
        payload.put("severity_rank", switch (severity) {
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        });
        payload.put("short_label", shortLabel);
        payload.put("operator_message", operatorMessage);
        payload.put("next_step", nextStep);
        payload.put("action_label", playbook != null && !playbook.isEmpty()
                ? "Открыть playbook"
                : switch (type) {
                    case "mandatory_field" -> "Заполнить поле";
                    case "source_of_truth" -> "Проверить источник";
                    case "priority_block" -> "Вернуть блок";
                    default -> "Исправить";
                });
        payload.put("analytics_message", analyticsMessage);
        if (playbook != null && !playbook.isEmpty()) {
            payload.put("playbook", playbook);
        }
        return payload;
    }

    private Map<String, String> resolveContextContractPlaybook(Map<String, Map<String, String>> playbooks,
                                                               String exactCode,
                                                               String scopedCode,
                                                               String type) {
        if (playbooks.isEmpty()) {
            return Map.of();
        }
        for (String key : List.of(
                normalizeMacroVariableKey(exactCode),
                normalizeMacroVariableKey(scopedCode),
                normalizeMacroVariableKey(type))) {
            if (StringUtils.hasText(key) && playbooks.containsKey(key)) {
                return playbooks.get(key);
            }
        }
        return Map.of();
    }

    private String resolveWorkspaceContextAttributeLabel(Map<String, Object> workspaceClient, String key) {
        if (workspaceClient.get("attribute_labels") instanceof Map<?, ?> labels) {
            for (Map.Entry<?, ?> entry : labels.entrySet()) {
                String normalizedKey = normalizeMacroVariableKey(String.valueOf(entry.getKey()));
                if (key.equals(normalizedKey)) {
                    Object labelRaw = entry.getValue();
                    String label = trimToNull(labelRaw == null ? null : String.valueOf(labelRaw));
                    if (StringUtils.hasText(label)) {
                        return label;
                    }
                }
            }
        }
        return humanizeMacroVariableLabel(key);
    }

    private String resolveWorkspaceContextSourceLabel(List<Map<String, Object>> contextSources, String sourceKey) {
        return contextSources.stream()
                .filter(item -> sourceKey.equals(normalizeMacroVariableKey(String.valueOf(item.get("key")))))
                .map(item -> {
                    Object labelRaw = item.get("label");
                    return trimToNull(labelRaw == null ? null : String.valueOf(labelRaw));
                })
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(humanizeMacroVariableLabel(sourceKey));
    }

    private String resolveWorkspaceContextBlockLabel(List<Map<String, Object>> contextBlocks, String blockKey) {
        return contextBlocks.stream()
                .filter(item -> blockKey.equals(normalizeMacroVariableKey(String.valueOf(item.get("key")))))
                .map(item -> {
                    Object labelRaw = item.get("label");
                    return trimToNull(labelRaw == null ? null : String.valueOf(labelRaw));
                })
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(humanizeMacroVariableLabel(blockKey));
    }

    private Map<String, Object> extractDialogConfig(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        dialogConfig.forEach((key, value) -> payload.put(String.valueOf(key), value));
        return payload;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = value != null ? String.valueOf(value).trim().toLowerCase(Locale.ROOT) : "";
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return switch (normalized) {
            case "1", "true", "yes", "on", "ok", "ready" -> true;
            default -> false;
        };
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

    private Map<String, List<String>> safeStringListMap(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = trimToNull(String.valueOf(entry.getKey()));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            List<String> items = safeStringList(entry.getValue());
            if (!items.isEmpty()) {
                result.put(key.toLowerCase(Locale.ROOT), items);
            }
        }
        return result;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeMacroVariableKey(String rawValue) {
        String value = trimToNull(rawValue);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private String humanizeMacroVariableLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return "Переменная";
        }
        return Stream.of(key.trim().toLowerCase(Locale.ROOT).split("_"))
                .filter(StringUtils::hasText)
                .map(token -> Character.toUpperCase(token.charAt(0)) + token.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse("Переменная");
    }
}
