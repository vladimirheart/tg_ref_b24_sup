package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DialogWorkspaceRolloutGovernanceService {

    private final DialogWorkspaceRolloutGovernanceConfigService configService;
    private final DialogWorkspaceRolloutParityService parityService;
    private final DialogWorkspaceRolloutLegacyInventoryService legacyInventoryService;
    private final DialogWorkspaceRolloutContextContractService contextContractService;
    private final DialogWorkspaceRolloutLegacyUsagePolicyService legacyUsagePolicyService;

    public DialogWorkspaceRolloutGovernanceService(DialogWorkspaceRolloutGovernanceConfigService configService,
                                                   DialogWorkspaceRolloutParityService parityService,
                                                   DialogWorkspaceRolloutLegacyInventoryService legacyInventoryService,
                                                   DialogWorkspaceRolloutContextContractService contextContractService,
                                                   DialogWorkspaceRolloutLegacyUsagePolicyService legacyUsagePolicyService) {
        this.configService = configService;
        this.parityService = parityService;
        this.legacyInventoryService = legacyInventoryService;
        this.contextContractService = contextContractService;
        this.legacyUsagePolicyService = legacyUsagePolicyService;
    }

    public Map<String, Object> buildWorkspaceRolloutPacket(Map<String, Object> totals,
                                                           Map<String, Object> guardrails,
                                                           Map<String, Object> rolloutDecision,
                                                           Map<String, Object> rolloutScorecard,
                                                           Object gapBreakdownRaw,
                                                           int windowDays,
                                                           String experimentName) {
        Map<String, Object> safeTotals = totals == null ? Map.of() : totals;
        Map<String, Object> safeGuardrails = guardrails == null ? Map.of() : guardrails;
        Map<String, Object> safeRolloutDecision = rolloutDecision == null ? Map.of() : rolloutDecision;
        Map<String, Object> safeRolloutScorecard = rolloutScorecard == null ? Map.of() : rolloutScorecard;
        Map<String, Object> gapBreakdown = gapBreakdownRaw instanceof Map<?, ?> map ? configService.castObjectMap(map) : Map.of();
        Map<String, Object> externalSignal = safeRolloutDecision.get("external_kpi_signal") instanceof Map<?, ?> map
                ? configService.castObjectMap(map)
                : Map.of();
        DialogWorkspaceRolloutGovernanceConfig config = configService.loadConfig();

        OffsetDateTime ownerSignoffAt = configService.parseReviewTimestamp(config.ownerSignoffAtRaw());
        boolean ownerSignoffTimestampInvalid = StringUtils.hasText(configService.normalizeNullString(config.ownerSignoffAtRaw()))
                && ownerSignoffAt == null;
        boolean ownerSignoffPresent = ownerSignoffAt != null && StringUtils.hasText(config.ownerSignoffBy());
        boolean ownerSignoffFresh = false;
        long ownerSignoffAgeHours = -1L;
        if (ownerSignoffAt != null) {
            ownerSignoffAgeHours = Math.max(0, java.time.Duration.between(ownerSignoffAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            ownerSignoffFresh = ownerSignoffAgeHours <= config.ownerSignoffTtlHours();
        }
        boolean ownerSignoffReady = !config.ownerSignoffRequired()
                || (ownerSignoffPresent && ownerSignoffFresh && !ownerSignoffTimestampInvalid);

        OffsetDateTime reviewCadenceAt = configService.parseReviewTimestamp(config.reviewCadenceAtRaw());
        boolean reviewCadenceTimestampInvalid = StringUtils.hasText(configService.normalizeNullString(config.reviewCadenceAtRaw()))
                && reviewCadenceAt == null;
        boolean reviewCadenceEnabled = config.reviewCadenceDays() > 0;
        boolean reviewCadencePresent = reviewCadenceAt != null && StringUtils.hasText(config.reviewCadenceBy());
        boolean reviewCadenceFresh = false;
        long reviewCadenceAgeDays = -1L;
        if (reviewCadenceAt != null) {
            reviewCadenceAgeDays = Math.max(0, java.time.Duration.between(reviewCadenceAt, OffsetDateTime.now(ZoneOffset.UTC)).toDays());
            reviewCadenceFresh = reviewCadenceAgeDays <= config.reviewCadenceDays();
        }

        long reviewConfirmedEvents = configService.toLong(safeTotals.get("workspace_rollout_review_confirmed_events"));
        long reviewDecisionGoEvents = configService.toLong(safeTotals.get("workspace_rollout_review_decision_go_events"));
        long reviewDecisionHoldEvents = configService.toLong(safeTotals.get("workspace_rollout_review_decision_hold_events"));
        long reviewDecisionRollbackEvents = configService.toLong(safeTotals.get("workspace_rollout_review_decision_rollback_events"));
        long reviewIncidentFollowupLinkedEvents = configService.toLong(safeTotals.get("workspace_rollout_review_incident_followup_linked_events"));

        List<Map<String, Object>> scorecardItems = configService.safeListOfMaps(safeRolloutScorecard.get("items"));
        boolean scorecardSnapshotReady = !scorecardItems.isEmpty();
        long workspaceOpenEvents = configService.toLong(safeTotals.get("workspace_open_events"));
        double parityReadyRate = configService.safeDouble(safeTotals.get("workspace_parity_ready_rate"));
        long parityGapEvents = configService.toLong(safeTotals.get("workspace_parity_gap_events"));
        List<Map<String, Object>> parityRows = configService.safeListOfMaps(gapBreakdown.get("parity"));
        boolean paritySnapshotReady = workspaceOpenEvents > 0 || !parityRows.isEmpty();
        String topParityReasons = parityRows.stream()
                .limit(3)
                .map(row -> {
                    String reason = configService.normalizeNullString(String.valueOf(row.getOrDefault("reason", "unspecified")));
                    long events = configService.toLong(row.get("events"));
                    return StringUtils.hasText(reason) ? "%s(%d)".formatted(reason, events) : "unspecified(%d)".formatted(events);
                })
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(", "));

        List<Map<String, Object>> alerts = configService.safeListOfMaps(safeGuardrails.get("alerts"));
        long renderErrorAlerts = alerts.stream().filter(alert -> "render_error_rate".equals(String.valueOf(alert.get("metric")))).count();
        long fallbackAlerts = alerts.stream().filter(alert -> "fallback_rate".equals(String.valueOf(alert.get("metric")))).count();
        long abandonAlerts = alerts.stream().filter(alert -> "abandon_rate".equals(String.valueOf(alert.get("metric")))).count();
        long slowOpenAlerts = alerts.stream().filter(alert -> "slow_open_rate".equals(String.valueOf(alert.get("metric")))).count();

        boolean reviewDecisionPresent = StringUtils.hasText(config.reviewDecisionAction());
        boolean reviewIncidentFollowupPresent = StringUtils.hasText(config.reviewIncidentFollowup());
        boolean reviewDecisionGo = "go".equals(config.reviewDecisionAction());
        OffsetDateTime previousDecisionAt = configService.parseReviewTimestamp(config.previousDecisionAtRaw());
        boolean previousDecisionTimestampInvalid = StringUtils.hasText(configService.normalizeNullString(config.previousDecisionAtRaw()))
                && previousDecisionAt == null;
        boolean previousDecisionNonGo = "hold".equals(config.previousDecisionAction())
                || "rollback".equals(config.previousDecisionAction());
        boolean followupForNonGoReady = !config.reviewFollowupForNonGoRequired()
                || !reviewDecisionGo
                || !previousDecisionNonGo
                || reviewIncidentFollowupPresent;
        List<String> reviewMissingCriteria = config.reviewRequiredCriteria().stream()
                .filter(criteria -> !config.reviewCheckedCriteria().contains(criteria))
                .toList();
        boolean reviewCriteriaReady = reviewMissingCriteria.isEmpty();
        boolean incidentActionRequiredNow = config.reviewIncidentFollowupRequired() && !alerts.isEmpty();
        boolean reviewCadenceReady = !reviewCadenceEnabled
                || (reviewCadencePresent && reviewCadenceFresh && !reviewCadenceTimestampInvalid
                && (!config.reviewDecisionRequired() || reviewDecisionPresent)
                && reviewCriteriaReady
                && (!config.reviewFollowupForNonGoRequired() || !previousDecisionTimestampInvalid)
                && followupForNonGoReady
                && (!incidentActionRequiredNow || reviewIncidentFollowupPresent));
        boolean incidentHistoryReady = true;
        boolean externalGateSnapshotReady = !externalSignal.isEmpty();

        DialogWorkspaceRolloutSectionResult paritySection = parityService.buildParityExitCriteria(config, experimentName);
        DialogWorkspaceRolloutSectionResult legacyInventorySection =
                legacyInventoryService.buildLegacyInventory(config, configService.normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at")));
        DialogWorkspaceRolloutSectionResult contextContractSection =
                contextContractService.buildContextContract(config, safeTotals);
        DialogWorkspaceRolloutSectionResult legacyUsagePolicySection =
                legacyUsagePolicyService.buildLegacyUsagePolicy(config, safeTotals, windowDays, experimentName);

        Map<String, Object> parityExitCriteria = paritySection.payload();
        boolean parityExitCriteriaEnabled = configService.toBoolean(parityExitCriteria.get("enabled"));

        List<Map<String, Object>> packetItems = new ArrayList<>();
        packetItems.add(buildScorecardItem(
                "scorecard_snapshot",
                "workspace",
                "Rollout scorecard snapshot",
                scorecardSnapshotReady ? "ok" : (config.packetRequired() ? "hold" : "attention"),
                config.packetRequired() && !scorecardSnapshotReady,
                "Пакет rollout должен включать актуальный scorecard для формального решения.",
                scorecardSnapshotReady
                        ? "items=%d, action=%s".formatted(scorecardItems.size(), String.valueOf(safeRolloutScorecard.getOrDefault("decision_action", safeRolloutDecision.getOrDefault("action", "hold"))))
                        : "missing",
                "scorecard available",
                configService.normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at")),
                null
        ));
        packetItems.add(buildScorecardItem(
                "parity_snapshot",
                "workspace",
                "Workspace parity snapshot",
                paritySnapshotReady ? "ok" : (config.packetRequired() ? "hold" : "attention"),
                config.packetRequired() && !paritySnapshotReady,
                "Пакет rollout должен фиксировать parity-gap snapshot по workspace vs legacy.",
                paritySnapshotReady
                        ? "opens=%d, ready=%.1f%%, gaps=%d".formatted(workspaceOpenEvents, parityReadyRate * 100d, parityGapEvents)
                        : "missing",
                "workspace_open_events > 0",
                configService.normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at")),
                StringUtils.hasText(topParityReasons) ? "top_reasons=" + topParityReasons : ""
        ));
        packetItems.add(buildScorecardItem(
                "external_gate_snapshot",
                "external_dependencies",
                "External KPI gate snapshot",
                externalGateSnapshotReady ? "ok" : (config.packetRequired() ? "hold" : "attention"),
                config.packetRequired() && !externalGateSnapshotReady,
                "Пакет rollout должен содержать статус external KPI gate и его риск-сигналы.",
                externalGateSnapshotReady
                        ? "enabled=%s, ready=%s, risk=%s".formatted(
                        configService.toBoolean(externalSignal.get("enabled")),
                        configService.toBoolean(externalSignal.get("ready_for_decision")),
                        String.valueOf(externalSignal.getOrDefault("datamart_risk_level", "low")))
                        : "missing",
                "external gate status present",
                configService.firstNonBlank(
                        configService.normalizeUtcTimestamp(externalSignal.get("reviewed_at")),
                        configService.normalizeUtcTimestamp(externalSignal.get("data_updated_at")),
                        configService.normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at"))),
                String.valueOf(externalSignal.getOrDefault("note", "")).trim()
        ));
        packetItems.add(buildScorecardItem(
                "incident_history",
                "guardrails",
                "Incident history snapshot",
                incidentHistoryReady ? "ok" : (config.packetRequired() ? "hold" : "attention"),
                config.packetRequired() && !incidentHistoryReady,
                "Пакет rollout должен содержать сводку guardrails/incident history за текущее UTC-окно.",
                "alerts=%d, render=%d, fallback=%d, abandon=%d, slow_open=%d".formatted(
                        alerts.size(), renderErrorAlerts, fallbackAlerts, abandonAlerts, slowOpenAlerts),
                "window=%d days UTC".formatted(Math.max(1, windowDays)),
                configService.normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at")),
                String.valueOf(safeGuardrails.getOrDefault("status", "ok"))
        ));
        packetItems.add(buildScorecardItem(
                "owner_signoff",
                "workspace",
                "Owner sign-off",
                !config.ownerSignoffRequired() ? "off" : (ownerSignoffReady ? "ok" : "hold"),
                config.ownerSignoffRequired() && !ownerSignoffReady,
                "Owner sign-off закрепляет единый decision loop для go / hold / rollback.",
                !config.ownerSignoffRequired()
                        ? "not required"
                        : ownerSignoffTimestampInvalid
                        ? "invalid_utc"
                        : ownerSignoffPresent
                        ? "signed_by=%s".formatted(config.ownerSignoffBy())
                        : "missing",
                config.ownerSignoffRequired() ? "present & <= %d h".formatted(config.ownerSignoffTtlHours()) : "optional",
                ownerSignoffAt != null ? ownerSignoffAt.toString() : "",
                ownerSignoffPresent ? "age_hours=%d".formatted(ownerSignoffAgeHours) : ""
        ));
        packetItems.add(buildScorecardItem(
                "weekly_review",
                "workspace",
                "Weekly parity review cadence",
                !reviewCadenceEnabled ? "off" : (reviewCadenceReady ? "ok" : "hold"),
                reviewCadenceEnabled && !reviewCadenceReady,
                "Parity-gap breakdown должен регулярно подтверждаться review в UTC, чтобы dual-run не оставался без владельца.",
                !reviewCadenceEnabled
                        ? "not required"
                        : reviewCadenceTimestampInvalid
                        ? "invalid_utc"
                        : reviewCadencePresent
                        ? "reviewed_by=%s%s%s".formatted(
                        config.reviewCadenceBy(),
                        reviewDecisionPresent ? ", decision=%s".formatted(config.reviewDecisionAction()) : "",
                        reviewIncidentFollowupPresent ? ", incident_followup=present" : "")
                        : "missing",
                reviewCadenceEnabled
                        ? "present & <= %d days%s%s".formatted(
                        config.reviewCadenceDays(),
                        config.reviewDecisionRequired() ? ", decision required" : "",
                        incidentActionRequiredNow ? ", incident follow-up required when alerts>0" : "")
                        + (config.reviewFollowupForNonGoRequired()
                        ? ", incident follow-up required for go after hold/rollback"
                        : "")
                        + (!config.reviewRequiredCriteria().isEmpty()
                        ? ", criteria required=%s".formatted(String.join("|", config.reviewRequiredCriteria()))
                        : "")
                        : "optional",
                reviewCadenceAt != null ? reviewCadenceAt.toString() : "",
                reviewCadencePresent
                        ? StringUtils.hasText(config.reviewCadenceNote())
                        ? "age_days=%d; note=%s".formatted(reviewCadenceAgeDays, config.reviewCadenceNote())
                        : "age_days=%d".formatted(reviewCadenceAgeDays)
                        + (!reviewMissingCriteria.isEmpty()
                        ? "; missing_criteria=%s".formatted(String.join("|", reviewMissingCriteria))
                        : "")
                        : config.reviewCadenceNote()
        ));
        packetItems.add(buildScorecardItemFromSection(paritySection));
        packetItems.add(buildScorecardItemFromSection(legacyInventorySection));
        packetItems.add(buildScorecardItemFromSection(legacyUsagePolicySection));
        packetItems.add(buildScorecardItemFromSection(contextContractSection));

        List<String> missingItems = packetItems.stream()
                .filter(item -> {
                    String status = String.valueOf(item.getOrDefault("status", "hold"));
                    return !"ok".equals(status) && !"off".equals(status);
                })
                .map(item -> String.valueOf(item.get("key")))
                .toList();
        long blockingCount = packetItems.stream()
                .filter(item -> "hold".equals(String.valueOf(item.getOrDefault("status", "hold"))))
                .count();
        long attentionCount = packetItems.stream()
                .filter(item -> "attention".equals(String.valueOf(item.getOrDefault("status", "attention"))))
                .count();
        long readyCount = packetItems.stream()
                .filter(item -> "ok".equals(String.valueOf(item.getOrDefault("status", "hold"))))
                .count();
        long offCount = packetItems.stream()
                .filter(item -> "off".equals(String.valueOf(item.getOrDefault("status", "hold"))))
                .count();
        List<String> invalidUtcItems = new ArrayList<>();
        if (ownerSignoffTimestampInvalid) {
            invalidUtcItems.add("owner_signoff");
        }
        if (reviewCadenceTimestampInvalid || previousDecisionTimestampInvalid) {
            invalidUtcItems.add("weekly_review");
        }
        if (configService.toBoolean(paritySection.payload().get("review_timestamp_invalid"))
                || String.valueOf(paritySection.payload().getOrDefault("error", "")).contains("invalid_utc")) {
            invalidUtcItems.add("parity_exit_criteria");
        }
        if (configService.toBoolean(legacyInventorySection.payload().get("review_timestamp_invalid"))) {
            invalidUtcItems.add("legacy_only_inventory");
        }
        if (configService.toBoolean(legacyUsagePolicySection.payload().get("review_timestamp_invalid"))) {
            invalidUtcItems.add("legacy_usage_policy");
        }
        if (configService.toBoolean(contextContractSection.payload().get("review_timestamp_invalid"))) {
            invalidUtcItems.add("context_minimum_profile");
        }
        invalidUtcItems.addAll(packetItems.stream()
                .filter(item -> String.valueOf(item.getOrDefault("current_value", "")).contains("invalid_utc")
                        || String.valueOf(item.getOrDefault("note", "")).contains("invalid_utc"))
                .map(item -> String.valueOf(item.get("key")))
                .filter(key -> !invalidUtcItems.contains(key))
                .toList());
        boolean packetReady = packetItems.stream().allMatch(item -> {
            String status = String.valueOf(item.getOrDefault("status", "hold"));
            return "ok".equals(status) || "off".equals(status);
        });

        String packetStatus;
        if (packetReady) {
            packetStatus = "ok";
        } else if (config.packetRequired()) {
            packetStatus = "hold";
        } else if (!scorecardSnapshotReady
                && workspaceOpenEvents <= 0
                && alerts.isEmpty()
                && !config.ownerSignoffRequired()
                && !reviewCadenceEnabled
                && !parityExitCriteriaEnabled
                && config.legacyOnlyScenarios().isEmpty()) {
            packetStatus = "off";
        } else {
            packetStatus = "attention";
        }

        Map<String, Object> ownerSignoff = new LinkedHashMap<>();
        ownerSignoff.put("required", config.ownerSignoffRequired());
        ownerSignoff.put("ready", ownerSignoffReady);
        ownerSignoff.put("signed_by", config.ownerSignoffBy());
        ownerSignoff.put("signed_at", ownerSignoffAt != null ? ownerSignoffAt.toString() : "");
        ownerSignoff.put("ttl_hours", config.ownerSignoffTtlHours());
        ownerSignoff.put("age_hours", ownerSignoffAgeHours);
        ownerSignoff.put("timestamp_invalid", ownerSignoffTimestampInvalid);

        Map<String, Object> reviewCadence = new LinkedHashMap<>();
        reviewCadence.put("enabled", reviewCadenceEnabled);
        reviewCadence.put("ready", reviewCadenceReady);
        reviewCadence.put("reviewed_by", config.reviewCadenceBy());
        reviewCadence.put("reviewed_at", reviewCadenceAt != null ? reviewCadenceAt.toString() : "");
        reviewCadence.put("cadence_days", config.reviewCadenceDays());
        reviewCadence.put("age_days", reviewCadenceAgeDays);
        reviewCadence.put("timestamp_invalid", reviewCadenceTimestampInvalid);
        reviewCadence.put("confirmed_events_in_window", reviewConfirmedEvents);
        reviewCadence.put("decision_go_events_in_window", reviewDecisionGoEvents);
        reviewCadence.put("decision_hold_events_in_window", reviewDecisionHoldEvents);
        reviewCadence.put("decision_rollback_events_in_window", reviewDecisionRollbackEvents);
        reviewCadence.put("incident_followup_linked_events_in_window", reviewIncidentFollowupLinkedEvents);
        reviewCadence.put("review_note", config.reviewCadenceNote() == null ? "" : config.reviewCadenceNote());
        reviewCadence.put("decision_action", config.reviewDecisionAction() == null ? "" : config.reviewDecisionAction());
        reviewCadence.put("incident_followup", config.reviewIncidentFollowup() == null ? "" : config.reviewIncidentFollowup());
        reviewCadence.put("decision_required", config.reviewDecisionRequired());
        reviewCadence.put("incident_followup_required", config.reviewIncidentFollowupRequired());
        reviewCadence.put("followup_after_non_go_required", config.reviewFollowupForNonGoRequired());
        reviewCadence.put("previous_decision_action", config.previousDecisionAction() == null ? "" : config.previousDecisionAction());
        reviewCadence.put("previous_decision_at", previousDecisionAt != null ? previousDecisionAt.toString() : "");
        reviewCadence.put("previous_decision_timestamp_invalid", previousDecisionTimestampInvalid);
        reviewCadence.put("followup_after_non_go_ready", followupForNonGoReady);
        reviewCadence.put("required_criteria", config.reviewRequiredCriteria());
        reviewCadence.put("checked_criteria", config.reviewCheckedCriteria());
        reviewCadence.put("missing_criteria", reviewMissingCriteria);
        reviewCadence.put("criteria_ready", reviewCriteriaReady);

        Map<String, Object> paritySnapshot = new LinkedHashMap<>();
        paritySnapshot.put("ready", paritySnapshotReady);
        paritySnapshot.put("workspace_open_events", workspaceOpenEvents);
        paritySnapshot.put("parity_gap_events", parityGapEvents);
        paritySnapshot.put("parity_ready_rate", parityReadyRate);
        paritySnapshot.put("top_reasons", parityRows.stream().limit(3).toList());

        Map<String, Object> incidentHistory = new LinkedHashMap<>();
        incidentHistory.put("ready", incidentHistoryReady);
        incidentHistory.put("window_days", Math.max(1, windowDays));
        incidentHistory.put("guardrail_status", String.valueOf(safeGuardrails.getOrDefault("status", "ok")));
        incidentHistory.put("alert_count", alerts.size());
        incidentHistory.put("render_error_alerts", renderErrorAlerts);
        incidentHistory.put("fallback_alerts", fallbackAlerts);
        incidentHistory.put("abandon_alerts", abandonAlerts);
        incidentHistory.put("slow_open_alerts", slowOpenAlerts);

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("generated_at", Instant.now().toString());
        packet.put("required", config.packetRequired());
        packet.put("packet_ready", packetReady);
        packet.put("status", packetStatus);
        packet.put("summary", packetReady
                ? "Governance packet complete."
                : (config.packetRequired() ? "Governance packet has blocking gaps." : "Governance packet is informative and has pending items."));
        packet.put("decision_action", String.valueOf(safeRolloutDecision.getOrDefault("action", "hold")));
        packet.put("missing_items", missingItems);
        packet.put("blocking_count", blockingCount);
        packet.put("attention_count", attentionCount);
        packet.put("ready_count", readyCount);
        packet.put("off_count", offCount);
        packet.put("invalid_utc_items", invalidUtcItems);
        packet.put("items", packetItems);
        packet.put("owner_signoff", ownerSignoff);
        packet.put("review_cadence", reviewCadence);
        packet.put("owner_signoff_expires_at_utc", ownerSignoffAt != null ? ownerSignoffAt.plusHours(config.ownerSignoffTtlHours()).toString() : "");
        packet.put("review_due_at_utc", reviewCadenceEnabled && reviewCadenceAt != null ? reviewCadenceAt.plusDays(config.reviewCadenceDays()).toString() : "");
        packet.put("next_review_at_utc", reviewCadenceEnabled && reviewCadenceAt != null ? reviewCadenceAt.plusDays(config.reviewCadenceDays()).toString() : "");
        packet.put("parity_snapshot", paritySnapshot);
        packet.put("parity_exit_criteria", paritySection.payload());
        packet.put("legacy_only_scenarios", config.legacyOnlyScenarios());
        packet.put("legacy_only_inventory", legacyInventorySection.payload());
        packet.put("incident_history", incidentHistory);
        packet.put("context_contract", contextContractSection.payload());
        packet.put("legacy_usage_policy", legacyUsagePolicySection.payload());
        packet.put("external_gate", Map.of(
                "ready", externalGateSnapshotReady,
                "enabled", configService.toBoolean(externalSignal.get("enabled")),
                "decision_ready", configService.toBoolean(externalSignal.get("ready_for_decision")),
                "risk_level", String.valueOf(externalSignal.getOrDefault("datamart_risk_level", "low")),
                "reviewed_at", configService.normalizeUtcTimestamp(externalSignal.get("reviewed_at"))
        ));
        return packet;
    }

    private Map<String, Object> buildScorecardItemFromSection(DialogWorkspaceRolloutSectionResult section) {
        return buildScorecardItem(
                section.key(),
                section.domain(),
                section.label(),
                section.status(),
                section.blocking(),
                section.rationale(),
                section.currentValue(),
                section.expectedValue(),
                section.recordedAt(),
                section.note()
        );
    }

    private Map<String, Object> buildScorecardItem(String key,
                                                   String domain,
                                                   String label,
                                                   String status,
                                                   boolean blocking,
                                                   String rationale,
                                                   String currentValue,
                                                   String expectedValue,
                                                   String recordedAt,
                                                   String note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("domain", domain);
        item.put("label", label);
        item.put("status", normalizeScorecardStatus(status));
        item.put("blocking", blocking);
        item.put("rationale", configService.normalizeNullString(rationale));
        item.put("current_value", configService.normalizeNullString(currentValue));
        item.put("expected_value", configService.normalizeNullString(expectedValue));
        item.put("recorded_at", configService.normalizeNullString(recordedAt));
        item.put("note", configService.normalizeNullString(note));
        return item;
    }

    private String normalizeScorecardStatus(String value) {
        String normalized = configService.normalizeNullString(value).toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "ok", "hold", "attention", "off" -> normalized;
            default -> "hold";
        };
    }
}
