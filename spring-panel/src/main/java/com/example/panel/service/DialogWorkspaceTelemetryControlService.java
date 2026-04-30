package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogWorkspaceTelemetryControlService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Map<String, Object> buildP1OperationalControl(Map<String, Object> payload) {
        Map<String, Object> totals = asMap(payload.get("totals"));
        Map<String, Object> previousTotals = asMap(payload.get("previous_totals"));
        Map<String, Object> rolloutPacket = asMap(payload.get("rollout_packet"));
        Map<String, Object> legacyInventory = asMap(rolloutPacket.get("legacy_only_inventory"));
        Map<String, Object> contextContract = asMap(rolloutPacket.get("context_contract"));

        long currentContextRate = asLong(totals.get("context_secondary_details_open_rate_pct"));
        long previousContextRate = asLong(previousTotals.get("context_secondary_details_open_rate_pct"));
        long currentExtraRate = asLong(totals.get("context_extra_attributes_open_rate_pct"));
        long previousExtraRate = asLong(previousTotals.get("context_extra_attributes_open_rate_pct"));
        long contextDeltaPct = currentContextRate - previousContextRate;
        long extraDeltaPct = currentExtraRate - previousExtraRate;

        String legacyStatus = Boolean.TRUE.equals(legacyInventory.get("review_queue_management_review_required"))
                ? "management_review"
                : Boolean.TRUE.equals(legacyInventory.get("review_queue_followup_required")) ? "followup" : "controlled";
        boolean contextManagementReviewRequired = Boolean.TRUE.equals(contextContract.get("secondary_noise_management_review_required"))
                || Boolean.TRUE.equals(totals.get("context_secondary_details_management_review_required"));
        boolean contextFollowupRequired = Boolean.TRUE.equals(contextContract.get("secondary_noise_followup_required"))
                || Boolean.TRUE.equals(totals.get("context_secondary_details_followup_required"));
        String contextStatus = contextManagementReviewRequired ? "management_review" : contextFollowupRequired ? "followup" : "controlled";
        String status = ("management_review".equals(legacyStatus) || "management_review".equals(contextStatus))
                ? "management_review"
                : ("followup".equals(legacyStatus) || "followup".equals(contextStatus)) ? "followup" : "controlled";

        Map<String, Object> control = new LinkedHashMap<>();
        control.put("status", status);
        control.put("summary", status.equals("controlled")
                ? "P1 operational control удерживается: legacy queue и context noise под наблюдением."
                : "P1 operational control требует follow-up для legacy queue или context noise.");
        control.put("legacy_status", legacyStatus);
        control.put("legacy_summary", firstNonBlank(
                String.valueOf(legacyInventory.getOrDefault("review_queue_management_review_summary", "")),
                String.valueOf(legacyInventory.getOrDefault("review_queue_summary", ""))));
        control.put("legacy_next_action_summary", String.valueOf(legacyInventory.getOrDefault("review_queue_next_action_summary", "")));
        control.put("legacy_management_review_count", asLong(legacyInventory.get("review_queue_escalated_count")));
        control.put("legacy_consolidation_count", asLong(legacyInventory.get("review_queue_consolidation_count")));
        control.put("context_status", contextStatus);
        control.put("context_summary", firstNonBlank(
                String.valueOf(contextContract.getOrDefault("secondary_noise_compaction_summary", "")),
                String.valueOf(totals.getOrDefault("context_secondary_details_compaction_summary", "")),
                String.valueOf(contextContract.getOrDefault("secondary_noise_summary", "")),
                String.valueOf(totals.getOrDefault("context_secondary_details_summary", ""))));
        control.put("context_noise_trend_status", contextDeltaPct >= 10L ? "rising" : contextDeltaPct <= -10L ? "improving" : "stable");
        control.put("context_noise_trend_delta_pct", contextDeltaPct);
        control.put("context_extra_attributes_delta_pct", extraDeltaPct);
        control.put("context_extra_attributes_compaction_candidate",
                Boolean.TRUE.equals(contextContract.get("extra_attributes_compaction_candidate"))
                        || Boolean.TRUE.equals(totals.get("context_extra_attributes_compaction_candidate")));
        control.put("next_action_summary", firstNonBlank(
                String.valueOf(legacyInventory.getOrDefault("review_queue_next_action_summary", "")),
                String.valueOf(contextContract.getOrDefault("secondary_noise_compaction_summary", "")),
                "P1 operational control не требует дополнительного follow-up."));
        control.put("management_review_required", "management_review".equals(status));
        return control;
    }

    public Map<String, Object> buildSlaReviewPathControl(Map<String, Object> payload,
                                                         Map<String, Object> slaPolicyAudit) {
        Map<String, Object> totals = asMap(payload.get("totals"));
        Map<String, Object> safeSlaAudit = slaPolicyAudit != null ? slaPolicyAudit : Map.of();
        boolean cheapPathConfirmed = Boolean.TRUE.equals(safeSlaAudit.get("cheap_review_path_confirmed"));
        boolean minimumPathReady = Boolean.TRUE.equals(safeSlaAudit.get("minimum_required_review_path_ready"));
        boolean churnFollowupRequired = Boolean.TRUE.equals(totals.get("workspace_sla_policy_churn_followup_required"))
                || Boolean.TRUE.equals(safeSlaAudit.get("weekly_review_followup_required"));
        boolean hasSlaSignals = !safeSlaAudit.isEmpty()
                || totals.containsKey("workspace_sla_policy_churn_followup_required")
                || totals.containsKey("workspace_sla_policy_churn_level");
        String leadTimeStatus = String.valueOf(safeSlaAudit.getOrDefault("decision_lead_time_status", "unknown"));
        String status = !hasSlaSignals
                ? "controlled"
                : cheapPathConfirmed
                ? "controlled"
                : minimumPathReady && !churnFollowupRequired
                ? "monitor"
                : churnFollowupRequired
                ? "followup"
                : "attention";

        Map<String, Object> control = new LinkedHashMap<>();
        control.put("status", status);
        control.put("summary", !hasSlaSignals
                ? "SLA review path не требует отдельного follow-up."
                : cheapPathConfirmed
                ? "Минимальный дешёвый SLA review path зафиксирован и удерживается под операционным контролем."
                : "SLA review path требует follow-up, чтобы остаться дешёвым и обязательным.");
        control.put("minimum_required_review_path_ready", minimumPathReady);
        control.put("minimum_required_review_path_summary", String.valueOf(safeSlaAudit.getOrDefault("minimum_required_review_path_summary", "")));
        control.put("cheap_review_path_confirmed", cheapPathConfirmed);
        control.put("decision_lead_time_status", leadTimeStatus);
        control.put("decision_lead_time_summary", String.valueOf(safeSlaAudit.getOrDefault("decision_lead_time_summary", "")));
        control.put("policy_churn_level", String.valueOf(totals.getOrDefault(
                "workspace_sla_policy_churn_level",
                safeSlaAudit.getOrDefault("policy_churn_risk_level", "controlled"))));
        control.put("next_action_summary", cheapPathConfirmed
                ? "Удерживайте только minimum required SLA review path и не возвращайте advisory checkpoints в типовые policy changes."
                : !hasSlaSignals
                ? "Дополнительный SLA follow-up не требуется."
                : Boolean.TRUE.equals(safeSlaAudit.get("advisory_path_reduction_candidate"))
                ? "Сократите advisory checkpoints до minimum required path и перепроверьте decision cadence."
                : minimumPathReady
                ? "Проверьте decision cadence: minimum required path уже готов, но lead time или churn ещё шумят."
                : "Закройте minimum required SLA review path перед следующими policy changes.");
        control.put("management_review_required", "followup".equals(status) && "high".equals(String.valueOf(
                safeSlaAudit.getOrDefault("policy_churn_risk_level", totals.getOrDefault("workspace_sla_policy_churn_level", "")))));
        return control;
    }

    public Map<String, Object> buildP2GovernanceControl(Map<String, Object> payload,
                                                        Map<String, Object> slaPolicyAudit,
                                                        Map<String, Object> macroGovernanceAudit) {
        Map<String, Object> totals = asMap(payload.get("totals"));
        Map<String, Object> previousTotals = asMap(payload.get("previous_totals"));
        Map<String, Object> safeSlaAudit = slaPolicyAudit != null ? slaPolicyAudit : Map.of();
        Map<String, Object> safeMacroAudit = macroGovernanceAudit != null ? macroGovernanceAudit : Map.of();
        boolean hasSlaAuditSignals = !safeSlaAudit.isEmpty();
        boolean hasMacroAuditSignals = !safeMacroAudit.isEmpty();

        long currentSlaChurnPct = asLong(totals.get("workspace_sla_policy_churn_ratio_pct"));
        long previousSlaChurnPct = asLong(previousTotals.get("workspace_sla_policy_churn_ratio_pct"));
        long slaChurnDeltaPct = currentSlaChurnPct - previousSlaChurnPct;
        String slaTrendStatus = slaChurnDeltaPct >= 25L ? "rising" : slaChurnDeltaPct <= -25L ? "improving" : "stable";
        long slaClosureRatePct = hasSlaAuditSignals ? asLong(safeSlaAudit.get("required_checkpoint_closure_rate_pct")) : 100L;
        long slaFreshnessRatePct = hasSlaAuditSignals ? asLong(safeSlaAudit.get("freshness_closure_rate_pct")) : 100L;
        String slaClosureStatus = slaClosureRatePct >= 100L ? "controlled" : "followup";
        String slaFreshnessStatus = slaFreshnessRatePct >= 100L ? "controlled" : "followup";

        boolean slaManagementReviewRequired = "high".equals(String.valueOf(safeSlaAudit.getOrDefault("cheap_path_drift_risk_level", "")))
                || "high".equals(String.valueOf(totals.getOrDefault("workspace_sla_policy_churn_level", "")));
        boolean slaFollowupRequired = Boolean.TRUE.equals(safeSlaAudit.get("weekly_review_followup_required"))
                || Boolean.TRUE.equals(totals.get("workspace_sla_policy_churn_followup_required"));
        String slaStatus = slaManagementReviewRequired ? "management_review" : slaFollowupRequired ? "followup" : "controlled";

        boolean macroManagementReviewRequired = !Boolean.TRUE.equals(safeMacroAudit.get("minimum_required_path_controlled"))
                && Boolean.TRUE.equals(safeMacroAudit.get("weekly_review_followup_required"));
        boolean macroFollowupRequired = Boolean.TRUE.equals(safeMacroAudit.get("advisory_followup_required"))
                || Boolean.TRUE.equals(safeMacroAudit.get("advisory_path_reduction_candidate"))
                || Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant"));
        String macroStatus = macroManagementReviewRequired ? "management_review" : macroFollowupRequired ? "followup" : "controlled";
        long macroClosureRatePct = hasMacroAuditSignals ? asLong(safeMacroAudit.get("required_checkpoint_closure_rate_pct")) : 100L;
        long macroFreshnessRatePct = hasMacroAuditSignals ? asLong(safeMacroAudit.get("freshness_closure_rate_pct")) : 100L;
        String macroClosureStatus = macroClosureRatePct >= 100L ? "controlled" : "followup";
        String macroFreshnessStatus = macroFreshnessRatePct >= 100L ? "controlled" : "followup";
        boolean macroActionableSignalDominant = asLong(safeMacroAudit.get("actionable_advisory_share_pct"))
                >= asLong(safeMacroAudit.get("low_signal_advisory_share_pct"))
                && !Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant"));
        String governanceClosureHealth = ("followup".equals(slaClosureStatus) || "followup".equals(slaFreshnessStatus)
                || "followup".equals(macroClosureStatus) || "followup".equals(macroFreshnessStatus))
                ? "followup"
                : "controlled";
        String macroNoiseHealth = Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant"))
                ? "followup"
                : macroActionableSignalDominant ? "controlled" : "monitor";
        String status = ("management_review".equals(slaStatus) || "management_review".equals(macroStatus))
                ? "management_review"
                : ("followup".equals(slaStatus) || "followup".equals(macroStatus))
                ? "followup"
                : "controlled";

        Map<String, Object> control = new LinkedHashMap<>();
        control.put("status", status);
        control.put("summary", "controlled".equals(status)
                ? "P2 governance control удерживается: SLA churn и macro noise остаются в рабочем диапазоне."
                : "P2 governance control требует follow-up для SLA churn-control или macro noise.");
        control.put("sla_status", slaStatus);
        control.put("sla_summary", firstNonBlank(
                String.valueOf(safeSlaAudit.getOrDefault("minimum_required_review_path_summary", "")),
                String.valueOf(safeSlaAudit.getOrDefault("weekly_review_summary", ""))));
        control.put("sla_churn_trend_status", slaTrendStatus);
        control.put("sla_churn_delta_pct", slaChurnDeltaPct);
        control.put("sla_cheap_path_drift_risk_level", String.valueOf(safeSlaAudit.getOrDefault("cheap_path_drift_risk_level", "controlled")));
        control.put("sla_typical_policy_change_ready", Boolean.TRUE.equals(safeSlaAudit.get("typical_policy_change_ready")));
        control.put("sla_closure_rate_pct", slaClosureRatePct);
        control.put("sla_closure_status", slaClosureStatus);
        control.put("sla_freshness_rate_pct", slaFreshnessRatePct);
        control.put("sla_freshness_status", slaFreshnessStatus);
        control.put("macro_status", macroStatus);
        control.put("macro_summary", firstNonBlank(
                String.valueOf(safeMacroAudit.getOrDefault("low_signal_backlog_summary", "")),
                String.valueOf(safeMacroAudit.getOrDefault("weekly_review_summary", ""))));
        control.put("macro_low_signal_backlog_dominant", Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant")));
        control.put("macro_actionable_advisory_share_pct", asLong(safeMacroAudit.get("actionable_advisory_share_pct")));
        control.put("macro_low_signal_advisory_share_pct", asLong(safeMacroAudit.get("low_signal_advisory_share_pct")));
        control.put("macro_actionable_signal_dominant", macroActionableSignalDominant);
        control.put("macro_closure_rate_pct", macroClosureRatePct);
        control.put("macro_closure_status", macroClosureStatus);
        control.put("macro_freshness_rate_pct", macroFreshnessRatePct);
        control.put("macro_freshness_status", macroFreshnessStatus);
        control.put("governance_closure_health", governanceClosureHealth);
        control.put("macro_noise_health", macroNoiseHealth);
        control.put("next_action_summary", firstNonBlank(
                Boolean.TRUE.equals(safeSlaAudit.get("advisory_path_reduction_candidate"))
                        ? "Сократите SLA advisory checkpoints для типовых policy changes и удерживайте cheap path."
                        : "",
                Boolean.TRUE.equals(safeMacroAudit.get("low_signal_backlog_dominant"))
                        ? "Оставьте low-signal macro red-list аналитическим и не превращайте его в ручной backlog."
                        : "",
                String.valueOf(safeSlaAudit.getOrDefault("weekly_review_summary", "")),
                String.valueOf(safeMacroAudit.getOrDefault("weekly_review_summary", "")),
                "P2 governance control не требует дополнительного follow-up."));
        control.put("management_review_required", "management_review".equals(status));
        return control;
    }

    public Map<String, Object> buildWorkspaceWeeklyReviewFocus(Map<String, Object> payload,
                                                               Map<String, Object> slaPolicyAudit,
                                                               Map<String, Object> macroGovernanceAudit) {
        Map<String, Object> totals = asMap(payload.get("totals"));
        Map<String, Object> previousTotals = asMap(payload.get("previous_totals"));
        Map<String, Object> rolloutPacket = asMap(payload.get("rollout_packet"));
        Map<String, Object> legacyInventory = asMap(rolloutPacket.get("legacy_only_inventory"));
        Map<String, Object> safeSlaAudit = slaPolicyAudit != null ? slaPolicyAudit : Map.of();
        Map<String, Object> safeMacroAudit = macroGovernanceAudit != null ? macroGovernanceAudit : Map.of();
        long previousContextSecondaryRatePct = asLong(previousTotals.get("context_secondary_details_open_rate_pct"));
        long currentContextSecondaryRatePct = asLong(totals.get("context_secondary_details_open_rate_pct"));
        long contextSecondaryDeltaPct = currentContextSecondaryRatePct - previousContextSecondaryRatePct;
        long previousContextExtraRatePct = asLong(previousTotals.get("context_extra_attributes_open_rate_pct"));
        long currentContextExtraRatePct = asLong(totals.get("context_extra_attributes_open_rate_pct"));
        long contextExtraDeltaPct = currentContextExtraRatePct - previousContextExtraRatePct;
        String contextTrendStatus = contextSecondaryDeltaPct >= 10L ? "rising" : contextSecondaryDeltaPct <= -10L ? "improving" : "stable";
        boolean contextExtraAttributesCompactionCandidate = Boolean.TRUE.equals(totals.get("context_extra_attributes_compaction_candidate"));
        boolean legacyManagementReviewRequired = Boolean.TRUE.equals(legacyInventory.get("review_queue_escalation_required"))
                || asLong(legacyInventory.get("review_queue_repeat_cycles")) >= 3L
                || asLong(legacyInventory.get("review_queue_oldest_overdue_days")) >= 7L;
        boolean contextManagementReviewRequired = Boolean.TRUE.equals(totals.get("context_secondary_details_management_review_required"))
                || ("heavy".equals(String.valueOf(totals.getOrDefault("context_secondary_details_usage_level", "")))
                && previousContextSecondaryRatePct >= 25L);
        boolean slaManagementReviewRequired = "high".equals(String.valueOf(safeSlaAudit.getOrDefault("policy_churn_risk_level", "")))
                || "high".equals(String.valueOf(totals.getOrDefault("workspace_sla_policy_churn_level", "")));
        boolean macroManagementReviewRequired = Boolean.TRUE.equals(safeMacroAudit.get("weekly_review_followup_required"))
                && !Boolean.TRUE.equals(safeMacroAudit.get("advisory_path_reduction_candidate"));

        List<Map<String, Object>> sections = new ArrayList<>();
        if (Boolean.TRUE.equals(legacyInventory.get("review_queue_followup_required"))
                || Boolean.TRUE.equals(legacyInventory.get("repeat_review_required"))) {
            sections.add(Map.of(
                    "key", "legacy",
                    "label", "Legacy closure loop",
                    "priority", "high",
                    "summary", String.valueOf(legacyInventory.getOrDefault("review_queue_summary", "")),
                    "management_review_required", legacyManagementReviewRequired,
                    "action_item", firstNonBlank(
                            String.valueOf(legacyInventory.getOrDefault("review_queue_next_action_summary", "")),
                            firstListItem(legacyInventory.get("action_items"), "Закройте weekly closure-loop для legacy review-queue."))
            ));
        }
        if (Boolean.TRUE.equals(totals.get("context_secondary_details_followup_required"))) {
            sections.add(Map.of(
                    "key", "context",
                    "label", "Context noise",
                    "priority", "medium",
                    "summary", firstNonBlank(
                            String.valueOf(totals.getOrDefault("context_secondary_details_compaction_summary", "")),
                            String.valueOf(totals.getOrDefault("context_extra_attributes_summary", "")),
                            String.valueOf(totals.getOrDefault("context_secondary_details_summary", ""))),
                    "trend_status", contextTrendStatus,
                    "trend_delta_pct", contextSecondaryDeltaPct,
                    "extra_attributes_delta_pct", contextExtraDeltaPct,
                    "management_review_required", contextManagementReviewRequired,
                    "action_item", contextExtraAttributesCompactionCandidate
                            ? "Ужмите extra attributes: оставьте в runtime sidebar только действительно используемые поля."
                            : "Проверьте, почему secondary context раскрывается слишком часто, и ужмите noisy blocks."
            ));
        }
        if (Boolean.TRUE.equals(totals.get("workspace_sla_policy_churn_followup_required"))
                || Boolean.TRUE.equals(safeSlaAudit.get("weekly_review_followup_required"))) {
            sections.add(Map.of(
                    "key", "sla",
                    "label", "SLA governance",
                    "priority", "high",
                    "summary", firstNonBlank(
                            String.valueOf(totals.getOrDefault("workspace_sla_policy_churn_summary", "")),
                            String.valueOf(safeSlaAudit.getOrDefault("weekly_review_summary", ""))),
                    "management_review_required", slaManagementReviewRequired,
                    "action_item", Boolean.TRUE.equals(safeSlaAudit.get("advisory_path_reduction_candidate"))
                            ? "Сократите advisory checkpoints для типовых SLA policy changes."
                            : "Закройте обязательный SLA review path и stabilise decision cadence."
            ));
        }
        if (Boolean.TRUE.equals(safeMacroAudit.get("weekly_review_followup_required"))
                || Boolean.TRUE.equals(safeMacroAudit.get("advisory_followup_required"))) {
            sections.add(Map.of(
                    "key", "macro",
                    "label", "Macro governance",
                    "priority", Boolean.TRUE.equals(safeMacroAudit.get("advisory_path_reduction_candidate")) ? "medium" : "high",
                    "summary", String.valueOf(safeMacroAudit.getOrDefault("weekly_review_summary", "")),
                    "management_review_required", macroManagementReviewRequired,
                    "action_item", Boolean.TRUE.equals(safeMacroAudit.get("advisory_path_reduction_candidate"))
                            ? "Оставьте low-signal red-list аналитическими и сократите ручной backlog."
                            : "Закройте обязательные macro checkpoints и только потом revisited advisory cleanup."
            ));
        }

        List<Map<String, Object>> sortedSections = sections.stream()
                .sorted((left, right) -> Integer.compare(
                        reviewFocusPriorityWeight(String.valueOf(left.get("priority"))),
                        reviewFocusPriorityWeight(String.valueOf(right.get("priority")))))
                .map(item -> {
                    Map<String, Object> enriched = new LinkedHashMap<>(item);
                    int priorityWeight = reviewFocusPriorityWeight(String.valueOf(item.get("priority")));
                    boolean managementReviewRequired = Boolean.TRUE.equals(item.get("management_review_required"));
                    enriched.put("priority_weight", priorityWeight);
                    enriched.put("followup_required", true);
                    enriched.put("management_review_required", managementReviewRequired);
                    enriched.put("section_status", managementReviewRequired ? "management_review" : priorityWeight == 0 ? "blocking" : "followup");
                    return enriched;
                })
                .toList();
        List<String> topActions = sortedSections.stream()
                .map(item -> trimToNull(String.valueOf(item.get("action_item"))))
                .filter(StringUtils::hasText)
                .limit(4)
                .toList();
        long blockingCount = sortedSections.stream().filter(item -> "high".equals(String.valueOf(item.get("priority")))).count();
        long managementReviewSectionCount = sortedSections.stream().filter(item -> Boolean.TRUE.equals(item.get("management_review_required"))).count();
        long focusScore = sortedSections.stream().mapToLong(item -> switch (String.valueOf(item.get("priority"))) {
            case "high" -> 3L;
            case "medium" -> 2L;
            default -> 1L;
        }).sum();
        boolean requiresManagementReview = blockingCount > 1
                || (blockingCount > 0 && sortedSections.size() > 2)
                || managementReviewSectionCount > 0;
        String status = sortedSections.isEmpty() ? "ok" : blockingCount > 0 ? "hold" : "attention";

        Map<String, Object> focus = new LinkedHashMap<>();
        focus.put("status", status);
        focus.put("summary", sortedSections.isEmpty()
                ? "Weekly review focus не требует дополнительных follow-up."
                : "Weekly review focus: %d секции(й), high=%d.".formatted(sortedSections.size(), blockingCount));
        focus.put("section_count", sortedSections.size());
        focus.put("followup_section_count", sortedSections.size());
        focus.put("blocking_count", blockingCount);
        focus.put("management_review_section_count", managementReviewSectionCount);
        focus.put("focus_score", focusScore);
        focus.put("focus_health", sortedSections.isEmpty() ? "stable" : requiresManagementReview ? "management_review" : "followup");
        focus.put("top_priority_key", sortedSections.isEmpty() ? "" : String.valueOf(sortedSections.get(0).getOrDefault("key", "")));
        focus.put("top_priority_label", sortedSections.isEmpty() ? "" : String.valueOf(sortedSections.get(0).getOrDefault("label", "")));
        focus.put("priority_mix_summary", sortedSections.isEmpty()
                ? "Weekly focus пуст: follow-up не требуется."
                : "high=%d, follow-up=%d, management-review=%d.".formatted(blockingCount, sortedSections.size(), managementReviewSectionCount));
        focus.put("next_action_summary", topActions.isEmpty() ? "Дополнительный follow-up не требуется." : topActions.get(0));
        focus.put("requires_management_review", requiresManagementReview);
        focus.put("sections", sortedSections);
        focus.put("top_actions", topActions);
        return focus;
    }

    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map
                ? OBJECT_MAPPER.convertValue(map, new TypeReference<Map<String, Object>>() {})
                : Map.of();
    }

    private int reviewFocusPriorityWeight(String value) {
        return switch (trimToNull(value) == null ? "" : trimToNull(value).toLowerCase(Locale.ROOT)) {
            case "high" -> 0;
            case "medium" -> 1;
            default -> 2;
        };
    }

    private String firstListItem(Object value, String fallback) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String candidate = trimToNull(String.valueOf(item));
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return fallback;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String candidate = trimToNull(value);
            if (candidate != null) {
                return candidate;
            }
        }
        return "";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
