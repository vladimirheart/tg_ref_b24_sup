package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class DialogWorkspaceRolloutContextContractService {

    private final DialogWorkspaceRolloutGovernanceConfigService configService;

    public DialogWorkspaceRolloutContextContractService(DialogWorkspaceRolloutGovernanceConfigService configService) {
        this.configService = configService;
    }

    public DialogWorkspaceRolloutSectionResult buildContextContract(DialogWorkspaceRolloutGovernanceConfig config,
                                                                   Map<String, Object> totals) {
        boolean enabled = config.contextContractRequired()
                || !config.contextContractScenarios().isEmpty()
                || !config.contextContractMandatoryFields().isEmpty()
                || !config.contextContractMandatoryFieldsByScenario().isEmpty()
                || !config.contextContractSourceOfTruth().isEmpty()
                || !config.contextContractSourceOfTruthByScenario().isEmpty()
                || !config.contextContractPriorityBlocks().isEmpty()
                || !config.contextContractPriorityBlocksByScenario().isEmpty()
                || !config.contextContractPlaybooks().isEmpty();
        OffsetDateTime reviewedAt = configService.parseReviewTimestamp(config.contextContractReviewedAtRaw());
        boolean reviewTimestampInvalid = StringUtils.hasText(configService.normalizeNullString(config.contextContractReviewedAtRaw()))
                && reviewedAt == null;
        boolean reviewPresent = reviewedAt != null && StringUtils.hasText(config.contextContractReviewedBy());
        boolean reviewFresh = false;
        long reviewAgeHours = -1L;
        if (reviewedAt != null) {
            reviewAgeHours = Math.max(0, Duration.between(reviewedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            reviewFresh = reviewAgeHours <= config.contextContractReviewTtlHours();
        }
        boolean definitionReady = !config.contextContractScenarios().isEmpty()
                && (!config.contextContractMandatoryFields().isEmpty() || !config.contextContractMandatoryFieldsByScenario().isEmpty())
                && (!config.contextContractSourceOfTruth().isEmpty() || !config.contextContractSourceOfTruthByScenario().isEmpty())
                && (!config.contextContractPriorityBlocks().isEmpty() || !config.contextContractPriorityBlocksByScenario().isEmpty());
        List<String> expectedKeys = configService.buildContextContractPlaybookExpectedKeys(config);
        List<String> missingKeys = expectedKeys.stream()
                .filter(key -> !configService.hasContextContractPlaybookCoverage(config.contextContractPlaybooks(), key))
                .toList();
        int expectedCount = expectedKeys.size();
        int coveredCount = Math.max(0, expectedCount - missingKeys.size());
        long coveragePct = expectedCount > 0 ? Math.round((coveredCount * 100d) / expectedCount) : 100L;
        boolean ready = !enabled || (definitionReady && reviewPresent && reviewFresh && !reviewTimestampInvalid);

        List<String> definitionGaps = Stream.of(
                        config.contextContractScenarios().isEmpty() ? "scenarios" : null,
                        (config.contextContractMandatoryFields().isEmpty() && config.contextContractMandatoryFieldsByScenario().isEmpty())
                                ? "mandatory_fields" : null,
                        (config.contextContractSourceOfTruth().isEmpty() && config.contextContractSourceOfTruthByScenario().isEmpty())
                                ? "source_of_truth" : null,
                        (config.contextContractPriorityBlocks().isEmpty() && config.contextContractPriorityBlocksByScenario().isEmpty())
                                ? "priority_blocks" : null)
                .filter(StringUtils::hasText)
                .toList();
        List<String> operatorFocusBlocks = Stream.concat(
                        config.contextContractPriorityBlocks().stream(),
                        config.contextContractPriorityBlocksByScenario().values().stream().flatMap(List::stream))
                .map(value -> value == null ? null : value.trim())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();
        List<String> actionItems = new ArrayList<>();
        if (reviewTimestampInvalid) {
            actionItems.add("Исправьте reviewed_at на валидный UTC timestamp.");
        } else if (enabled && !reviewPresent) {
            actionItems.add("Подтвердите context contract через UTC review-checkpoint.");
        } else if (enabled && !reviewFresh) {
            actionItems.add("Обновите review context contract: текущий sign-off устарел.");
        }
        if (!definitionGaps.isEmpty()) {
            actionItems.add("Заполните missing contract definitions: " + String.join(", ", definitionGaps) + ".");
        }
        if (!missingKeys.isEmpty()) {
            actionItems.add("Добавьте playbooks для gap-ключей: " + String.join(", ", missingKeys.stream().limit(3).toList()) + ".");
        }
        if (operatorFocusBlocks.isEmpty() && enabled) {
            actionItems.add("Задайте priority blocks, чтобы снизить шум в sidebar и сделать раскрытие progressive.");
        }
        String operatorSummary = ready
                ? "Minimum profile соблюдён."
                : reviewTimestampInvalid
                ? "Review checkpoint содержит невалидный UTC timestamp."
                : !definitionGaps.isEmpty()
                ? "Contract definitions требуют cleanup."
                : !missingKeys.isEmpty()
                ? "Playbook coverage неполный для operator-flow."
                : (enabled && !reviewPresent)
                ? "Context contract ещё не подтверждён review-checkpoint."
                : (enabled && !reviewFresh)
                ? "Context contract review устарел."
                : !operatorFocusBlocks.isEmpty()
                ? "Operator focus blocks требуют приоритизации."
                : "Context contract требует action-oriented follow-up.";
        String nextStepSummary = actionItems.isEmpty() ? "" : actionItems.get(0);

        boolean secondaryFollowupRequired = configService.toBoolean(totals.get("context_secondary_details_followup_required"));
        boolean secondaryManagementReviewRequired = configService.toBoolean(totals.get("context_secondary_details_management_review_required"));
        String secondarySummary = String.valueOf(totals.getOrDefault("context_secondary_details_summary", ""));
        String secondaryCompactionSummary = String.valueOf(totals.getOrDefault("context_secondary_details_compaction_summary", ""));
        String secondaryUsageLevel = String.valueOf(totals.getOrDefault("context_secondary_details_usage_level", "rare"));
        String secondaryTopSection = String.valueOf(totals.getOrDefault("context_secondary_details_top_section", ""));
        boolean extraAttributesCompactionCandidate = configService.toBoolean(totals.get("context_extra_attributes_compaction_candidate"));
        long extraAttributesOpenRatePct = configService.toLong(totals.get("context_extra_attributes_open_rate_pct"));
        long extraAttributesSharePctOfSecondary = configService.toLong(totals.get("context_extra_attributes_share_pct_of_secondary"));
        String extraAttributesUsageLevel = String.valueOf(totals.getOrDefault("context_extra_attributes_usage_level", "rare"));
        String extraAttributesSummary = String.valueOf(totals.getOrDefault("context_extra_attributes_summary", ""));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("required", config.contextContractRequired());
        payload.put("ready", ready);
        payload.put("reviewed_by", config.contextContractReviewedBy() == null ? "" : config.contextContractReviewedBy());
        payload.put("reviewed_at", reviewedAt != null ? reviewedAt.toString() : "");
        payload.put("review_note", config.contextContractReviewNote() == null ? "" : config.contextContractReviewNote());
        payload.put("review_ttl_hours", config.contextContractReviewTtlHours());
        payload.put("review_age_hours", reviewAgeHours);
        payload.put("review_timestamp_invalid", reviewTimestampInvalid);
        payload.put("scenarios", config.contextContractScenarios());
        payload.put("mandatory_fields", config.contextContractMandatoryFields());
        payload.put("mandatory_fields_by_scenario", config.contextContractMandatoryFieldsByScenario());
        payload.put("source_of_truth", config.contextContractSourceOfTruth());
        payload.put("source_of_truth_by_scenario", config.contextContractSourceOfTruthByScenario());
        payload.put("priority_blocks", config.contextContractPriorityBlocks());
        payload.put("priority_blocks_by_scenario", config.contextContractPriorityBlocksByScenario());
        payload.put("playbooks", config.contextContractPlaybooks());
        payload.put("playbook_count", config.contextContractPlaybooks().size());
        payload.put("playbook_expected_count", expectedCount);
        payload.put("playbook_covered_count", coveredCount);
        payload.put("playbook_coverage_pct", coveragePct);
        payload.put("playbook_missing_keys", missingKeys);
        payload.put("definition_ready", definitionReady);
        payload.put("definition_gaps", definitionGaps);
        payload.put("operator_focus_blocks", operatorFocusBlocks);
        payload.put("progressive_disclosure_ready", !operatorFocusBlocks.isEmpty());
        payload.put("operator_summary", operatorSummary);
        payload.put("next_step_summary", nextStepSummary);
        payload.put("action_items", actionItems);
        payload.put("secondary_noise_followup_required", secondaryFollowupRequired);
        payload.put("secondary_noise_management_review_required", secondaryManagementReviewRequired);
        payload.put("secondary_noise_summary", secondarySummary);
        payload.put("secondary_noise_compaction_summary", secondaryCompactionSummary);
        payload.put("secondary_noise_usage_level", secondaryUsageLevel);
        payload.put("secondary_noise_top_section", secondaryTopSection);
        payload.put("extra_attributes_compaction_candidate", extraAttributesCompactionCandidate);
        payload.put("extra_attributes_open_rate_pct", extraAttributesOpenRatePct);
        payload.put("extra_attributes_share_pct_of_secondary", extraAttributesSharePctOfSecondary);
        payload.put("extra_attributes_usage_level", extraAttributesUsageLevel);
        payload.put("extra_attributes_summary", extraAttributesSummary);

        String currentValue = !enabled
                ? "not required"
                : reviewTimestampInvalid
                ? "invalid_utc"
                : "scenarios=%d, fields=%d, scenario_profiles=%d, sources=%d, blocks=%d, playbooks=%d/%d (%d%%)".formatted(
                config.contextContractScenarios().size(),
                config.contextContractMandatoryFields().size(),
                config.contextContractMandatoryFieldsByScenario().size(),
                config.contextContractSourceOfTruth().size() + config.contextContractSourceOfTruthByScenario().size(),
                config.contextContractPriorityBlocks().size() + config.contextContractPriorityBlocksByScenario().size(),
                coveredCount,
                expectedCount,
                coveragePct);
        String note = definitionReady
                ? configService.firstNonBlank(
                config.contextContractReviewNote(),
                "reviewed_by=%s; age_hours=%d".formatted(
                        StringUtils.hasText(config.contextContractReviewedBy()) ? config.contextContractReviewedBy() : "n/a",
                        reviewAgeHours))
                : "missing=" + String.join(", ", definitionGaps);

        return new DialogWorkspaceRolloutSectionResult(
                "context_minimum_profile",
                "context",
                "Customer context minimum profile",
                !enabled ? "off" : (ready ? "ok" : (config.contextContractRequired() ? "hold" : "attention")),
                config.contextContractRequired() && !ready,
                "Minimum customer context должен быть формализован по сценариям: mandatory fields, source-of-truth, priority blocks и UTC-review.",
                currentValue,
                enabled ? "scenarios + mandatory/source/priority definitions + review <= %d h UTC".formatted(config.contextContractReviewTtlHours()) : "optional",
                reviewedAt != null ? reviewedAt.toString() : "",
                note,
                payload
        );
    }
}
