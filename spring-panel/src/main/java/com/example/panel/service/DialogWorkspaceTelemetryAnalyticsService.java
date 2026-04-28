package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DialogWorkspaceTelemetryAnalyticsService {

    private static final List<String> DEFAULT_REQUIRED_PRIMARY_KPIS = List.of("frt", "ttr", "sla_breach");
    private static final long DEFAULT_MIN_KPI_EVENTS_FOR_DECISION = 10L;
    private static final double DEFAULT_MIN_KPI_COVERAGE_RATE_FOR_DECISION = 0.05d;
    private static final long DEFAULT_KPI_OUTCOME_MIN_SAMPLES_PER_COHORT = 5L;
    private static final double DEFAULT_KPI_OUTCOME_FRT_MAX_RELATIVE_REGRESSION = 0.10d;
    private static final double DEFAULT_KPI_OUTCOME_TTR_MAX_RELATIVE_REGRESSION = 0.10d;
    private static final double DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_ABSOLUTE_DELTA = 0.02d;
    private static final double DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_RELATIVE_MULTIPLIER = 1.20d;
    private static final double DEFAULT_WORKSPACE_WINNER_MIN_OPEN_IMPROVEMENT = 0d;
    private static final Set<String> DEFAULT_REQUIRED_KPI_OUTCOME_KEYS = Set.of("frt", "ttr", "sla_breach");
    private static final double DEFAULT_GUARDRAIL_RENDER_ERROR_RATE = 0.01d;
    private static final double DEFAULT_GUARDRAIL_FALLBACK_RATE = 0.03d;
    private static final double DEFAULT_GUARDRAIL_ABANDON_RATE = 0.10d;
    private static final double DEFAULT_GUARDRAIL_SLOW_OPEN_RATE = 0.05d;
    private static final int DEFAULT_DIMENSION_MIN_EVENTS = 20;
    private static final int DEFAULT_COHORT_MIN_EVENTS = 30;

    private final SharedConfigService sharedConfigService;

    public DialogWorkspaceTelemetryAnalyticsService(SharedConfigService sharedConfigService) {
        this.sharedConfigService = sharedConfigService;
    }

    private Map<String, Object> castObjectMap(Map<?, ?> source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        source.forEach((key, value) -> payload.put(String.valueOf(key), value));
        return payload;
    }

    private boolean toBoolean(Object value) {
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
    public Map<String, Object> buildWorkspaceCohortComparison(List<Map<String, Object>> rows,
                                                               Map<String, Object> telemetryConfig) {
        List<Map<String, Object>> safeRows = rows == null ? List.of() : rows;
        List<Map<String, Object>> controlRows = safeRows.stream()
                .filter(row -> "control".equalsIgnoreCase(String.valueOf(row.get("experiment_cohort"))))
                .toList();
        List<Map<String, Object>> testRows = safeRows.stream()
                .filter(row -> "test".equalsIgnoreCase(String.valueOf(row.get("experiment_cohort"))))
                .toList();

        Map<String, Object> controlTotals = computeWorkspaceTelemetryTotals(controlRows);
        Map<String, Object> testTotals = computeWorkspaceTelemetryTotals(testRows);

        long controlEvents = toLong(controlTotals.get("events"));
        long testEvents = toLong(testTotals.get("events"));

        double controlRenderRate = safeRate(toLong(controlTotals.get("render_errors")), controlEvents);
        double testRenderRate = safeRate(toLong(testTotals.get("render_errors")), testEvents);
        double controlFallbackRate = safeRate(toLong(controlTotals.get("fallbacks")), controlEvents);
        double testFallbackRate = safeRate(toLong(testTotals.get("fallbacks")), testEvents);
        double controlAbandonRate = safeRate(toLong(controlTotals.get("abandons")), controlEvents);
        double testAbandonRate = safeRate(toLong(testTotals.get("abandons")), testEvents);
        double controlSlowOpenRate = safeRate(toLong(controlTotals.get("slow_open_events")), controlEvents);
        double testSlowOpenRate = safeRate(toLong(testTotals.get("slow_open_events")), testEvents);

        Long controlAvgOpen = extractNullableLong(controlTotals.get("avg_open_ms"));
        Long testAvgOpen = extractNullableLong(testTotals.get("avg_open_ms"));
        double minOpenImprovementPercent = resolveWinnerMinOpenImprovementPercent();

        long minCohortEvents = resolveLongConfig(telemetryConfig, "cohort_min_events", DEFAULT_COHORT_MIN_EVENTS, 5, 1000);
        boolean enoughData = controlEvents >= minCohortEvents && testEvents >= minCohortEvents;
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("control", controlTotals);
        comparison.put("test", testTotals);
        comparison.put("sample_size_ok", enoughData);
        comparison.put("sample_size_min_events", minCohortEvents);
        comparison.put("control_events", controlEvents);
        comparison.put("test_events", testEvents);
        comparison.put("render_error_rate_delta", testRenderRate - controlRenderRate);
        comparison.put("fallback_rate_delta", testFallbackRate - controlFallbackRate);
        comparison.put("abandon_rate_delta", testAbandonRate - controlAbandonRate);
        comparison.put("slow_open_rate_delta", testSlowOpenRate - controlSlowOpenRate);
        comparison.put("avg_open_ms_delta", (testAvgOpen != null && controlAvgOpen != null)
                ? testAvgOpen - controlAvgOpen
                : null);
        comparison.put("winner_min_open_improvement_percent", minOpenImprovementPercent * 100d);
        comparison.put("kpi_signal", buildPrimaryKpiSignal(controlTotals, testTotals, controlEvents, testEvents));
        comparison.put("kpi_outcome_signal", buildPrimaryKpiOutcomeSignal(controlTotals, testTotals));
        comparison.put("winner", resolveWorkspaceCohortWinner(
                enoughData,
                testAvgOpen,
                controlAvgOpen,
                testRenderRate,
                controlRenderRate,
                testFallbackRate,
                controlFallbackRate,
                testAbandonRate,
                controlAbandonRate,
                testSlowOpenRate,
                controlSlowOpenRate,
                minOpenImprovementPercent));
        return comparison;
    }

    private String resolveWorkspaceCohortWinner(boolean enoughData,
                                                Long testAvgOpen,
                                                Long controlAvgOpen,
                                                double testRenderRate,
                                                double controlRenderRate,
                                                double testFallbackRate,
                                                double controlFallbackRate,
                                                double testAbandonRate,
                                                double controlAbandonRate,
                                                double testSlowOpenRate,
                                                double controlSlowOpenRate,
                                                double minOpenImprovementPercent) {
        if (!enoughData || testAvgOpen == null || controlAvgOpen == null) {
            return "insufficient_data";
        }
        boolean technicalRegressions = testRenderRate > controlRenderRate
                || testFallbackRate > controlFallbackRate
                || testAbandonRate > controlAbandonRate
                || testSlowOpenRate > controlSlowOpenRate;
        if (technicalRegressions) {
            return "control";
        }
        double relativeImprovement = controlAvgOpen > 0
                ? (double) (controlAvgOpen - testAvgOpen) / controlAvgOpen
                : 0d;
        return relativeImprovement >= minOpenImprovementPercent ? "test" : "control";
    }

    public Map<String, Object> computeWorkspaceTelemetryTotals(List<Map<String, Object>> rows) {
        Map<String, Object> totals = new LinkedHashMap<>();
        long events = rows.stream().mapToLong(row -> toLong(row.get("events"))).sum();
        long renderErrors = rows.stream().mapToLong(row -> toLong(row.get("render_errors"))).sum();
        long fallbacks = rows.stream().mapToLong(row -> toLong(row.get("fallbacks"))).sum();
        long abandons = rows.stream().mapToLong(row -> toLong(row.get("abandons"))).sum();
        long slowOpenEvents = rows.stream().mapToLong(row -> toLong(row.get("slow_open_events"))).sum();
        long frtKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_frt_events"))).sum();
        long ttrKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_ttr_events"))).sum();
        long slaBreachKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_sla_breach_events"))).sum();
        long dialogsPerShiftKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_dialogs_per_shift_events"))).sum();
        long csatKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_csat_events"))).sum();
        long workspaceOpenEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_open_events"))).sum();
        long contextProfileGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_profile_gap_events"))).sum();
        long contextSourceGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_source_gap_events"))).sum();
        long contextAttributePolicyGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_attribute_policy_gap_events"))).sum();
        long contextBlockGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_block_gap_events"))).sum();
        long contextContractGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_contract_gap_events"))).sum();
        long contextSourcesExpandedEvents = rows.stream().mapToLong(row -> toLong(row.get("context_sources_expanded_events"))).sum();
        long contextAttributePolicyExpandedEvents = rows.stream().mapToLong(row -> toLong(row.get("context_attribute_policy_expanded_events"))).sum();
        long contextExtraAttributesExpandedEvents = rows.stream().mapToLong(row -> toLong(row.get("context_extra_attributes_expanded_events"))).sum();
        long workspaceSlaPolicyGapEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_sla_policy_gap_events"))).sum();
        long workspaceParityGapEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_parity_gap_events"))).sum();
        long workspaceInlineNavigationEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_inline_navigation_events"))).sum();
        long manualLegacyOpenEvents = rows.stream().mapToLong(row -> toLong(row.get("manual_legacy_open_events"))).sum();
        long workspaceOpenLegacyBlockedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_open_legacy_blocked_events"))).sum();
        long workspaceRolloutPacketViewedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_packet_viewed_events"))).sum();
        long workspaceRolloutReviewConfirmedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_confirmed_events"))).sum();
        long workspaceRolloutReviewDecisionGoEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_decision_go_events"))).sum();
        long workspaceRolloutReviewDecisionHoldEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_decision_hold_events"))).sum();
        long workspaceRolloutReviewDecisionRollbackEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_decision_rollback_events"))).sum();
        long workspaceRolloutReviewIncidentFollowupLinkedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_incident_followup_linked_events"))).sum();
        long workspaceSlaPolicyReviewUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_sla_policy_review_updated_events"))).sum();
        long workspaceSlaPolicyDecisionEvents = rows.stream()
                .mapToLong(row -> toLong(row.get("workspace_rollout_review_decision_go_events"))
                        + toLong(row.get("workspace_rollout_review_decision_hold_events"))
                        + toLong(row.get("workspace_rollout_review_decision_rollback_events")))
                .sum();
        long workspaceMacroGovernanceReviewUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_macro_governance_review_updated_events"))).sum();
        long workspaceMacroExternalCatalogPolicyUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_macro_external_catalog_policy_updated_events"))).sum();
        long workspaceMacroDeprecationPolicyUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_macro_deprecation_policy_updated_events"))).sum();
        long workspaceMacroPolicyUpdateEvents = workspaceMacroGovernanceReviewUpdatedEvents
                + workspaceMacroExternalCatalogPolicyUpdatedEvents
                + workspaceMacroDeprecationPolicyUpdatedEvents;
        long frtRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_frt_recorded_events"))).sum();
        long ttrRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_ttr_recorded_events"))).sum();
        long slaBreachRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_sla_breach_recorded_events"))).sum();
        long dialogsPerShiftRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_dialogs_per_shift_recorded_events"))).sum();
        long csatRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_csat_recorded_events"))).sum();

        long weightedOpenCount = rows.stream()
                .mapToLong(this::resolveWorkspaceOpenWeight)
                .sum();
        long weightedOpenSum = rows.stream()
                .mapToLong(row -> {
                    Long avgOpenMs = extractNullableLong(row.get("avg_open_ms"));
                    if (avgOpenMs == null) {
                        return 0L;
                    }
                    long rowWeight = resolveWorkspaceOpenWeight(row);
                    return avgOpenMs * rowWeight;
                })
                .sum();
        Long avgOpenMs = weightedOpenCount > 0 ? Math.round((double) weightedOpenSum / weightedOpenCount) : null;
        Long avgFrtMs = weightedAverage(rows, "kpi_frt_recorded_events", "avg_frt_ms");
        long workspaceLegacyUsagePolicyUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_legacy_usage_policy_updated_events"))).sum();
        Long avgTtrMs = weightedAverage(rows, "kpi_ttr_recorded_events", "avg_ttr_ms");
        long contextSecondaryDetailsExpandedEvents = contextSourcesExpandedEvents
                + contextAttributePolicyExpandedEvents
                + contextExtraAttributesExpandedEvents;
        long contextSecondaryDetailsOpenRatePct = workspaceOpenEvents > 0
                ? Math.round((contextSecondaryDetailsExpandedEvents * 100d) / workspaceOpenEvents)
                : 0L;
        long contextExtraAttributesOpenRatePct = workspaceOpenEvents > 0
                ? Math.round((contextExtraAttributesExpandedEvents * 100d) / workspaceOpenEvents)
                : 0L;
        String contextSecondaryDetailsUsageLevel = contextSecondaryDetailsOpenRatePct >= 40L
                ? "heavy"
                : contextSecondaryDetailsOpenRatePct >= 15L
                ? "moderate"
                : "rare";
        String contextExtraAttributesUsageLevel = contextExtraAttributesOpenRatePct >= 20L
                ? "heavy"
                : contextExtraAttributesOpenRatePct >= 8L
                ? "moderate"
                : "rare";
        String contextSecondaryDetailsTopSection = Stream.of(
                        Map.entry("sources", contextSourcesExpandedEvents),
                        Map.entry("attribute_policy", contextAttributePolicyExpandedEvents),
                        Map.entry("extra_attributes", contextExtraAttributesExpandedEvents))
                .filter(entry -> entry.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        boolean contextSecondaryDetailsFollowupRequired = contextSecondaryDetailsOpenRatePct >= 25L
                || contextExtraAttributesExpandedEvents > (contextSourcesExpandedEvents + contextAttributePolicyExpandedEvents);
        String contextSecondaryDetailsSummary = contextSecondaryDetailsExpandedEvents <= 0
                ? "Вторичные context-блоки почти не раскрывались."
                : "Secondary context открывали %d раз (%d%% от workspace opens); top=%s."
                .formatted(
                        contextSecondaryDetailsExpandedEvents,
                        contextSecondaryDetailsOpenRatePct,
                        StringUtils.hasText(contextSecondaryDetailsTopSection) ? contextSecondaryDetailsTopSection : "n/a");
        long contextExtraAttributesSharePctOfSecondary = contextSecondaryDetailsExpandedEvents > 0
                ? Math.round((contextExtraAttributesExpandedEvents * 100d) / contextSecondaryDetailsExpandedEvents)
                : 0L;
        boolean contextExtraAttributesCompactionCandidate = contextExtraAttributesOpenRatePct >= 15L
                || (contextExtraAttributesExpandedEvents > 0
                && "extra_attributes".equals(contextSecondaryDetailsTopSection)
                && contextSecondaryDetailsFollowupRequired);
        boolean contextSecondaryDetailsManagementReviewRequired = contextExtraAttributesCompactionCandidate
                && "heavy".equals(contextSecondaryDetailsUsageLevel);
        String contextExtraAttributesSummary = contextExtraAttributesExpandedEvents <= 0
                ? "Extra attributes почти не раскрывались."
                : "Extra attributes открывали %d раз (%d%% от workspace opens); usage=%s."
                .formatted(
                        contextExtraAttributesExpandedEvents,
                        contextExtraAttributesOpenRatePct,
                        contextExtraAttributesUsageLevel);
        String contextSecondaryDetailsCompactionSummary = contextExtraAttributesCompactionCandidate
                ? "Extra attributes формируют %d%% secondary-context opens; стоит ужать hidden attributes."
                .formatted(contextExtraAttributesSharePctOfSecondary)
                : "Secondary context pressure остаётся под контролем.";
        long workspaceSlaPolicyChurnRatioPct = workspaceSlaPolicyDecisionEvents > 0
                ? Math.round((workspaceSlaPolicyReviewUpdatedEvents * 100d) / workspaceSlaPolicyDecisionEvents)
                : (workspaceSlaPolicyReviewUpdatedEvents > 0 ? 100L : 0L);
        long workspaceSlaPolicyDecisionCoveragePct = workspaceSlaPolicyReviewUpdatedEvents > 0
                ? Math.round((workspaceSlaPolicyDecisionEvents * 100d) / workspaceSlaPolicyReviewUpdatedEvents)
                : 100L;
        String workspaceSlaPolicyChurnLevel = workspaceSlaPolicyChurnRatioPct >= 200L
                ? "high"
                : workspaceSlaPolicyChurnRatioPct >= 100L
                ? "moderate"
                : "controlled";
        boolean workspaceSlaPolicyChurnFollowupRequired = workspaceSlaPolicyChurnRatioPct >= 150L
                || (workspaceSlaPolicyReviewUpdatedEvents > 0 && workspaceSlaPolicyDecisionCoveragePct < 60L);
        String workspaceSlaPolicyChurnSummary = workspaceSlaPolicyReviewUpdatedEvents <= 0
                ? "SLA policy review updates в telemetry окне не зафиксированы."
                : "SLA policy updates=%d, decisions=%d, churn=%d%%."
                .formatted(
                        workspaceSlaPolicyReviewUpdatedEvents,
                        workspaceSlaPolicyDecisionEvents,
                        workspaceSlaPolicyChurnRatioPct);

        totals.put("events", events);
        totals.put("render_errors", renderErrors);
        totals.put("fallbacks", fallbacks);
        totals.put("abandons", abandons);
        totals.put("slow_open_events", slowOpenEvents);
        totals.put("kpi_frt_events", frtKpiEvents);
        totals.put("kpi_ttr_events", ttrKpiEvents);
        totals.put("kpi_sla_breach_events", slaBreachKpiEvents);
        totals.put("kpi_dialogs_per_shift_events", dialogsPerShiftKpiEvents);
        totals.put("kpi_csat_events", csatKpiEvents);
        totals.put("workspace_open_events", workspaceOpenEvents);
        totals.put("context_profile_gap_events", contextProfileGapEvents);
        totals.put("context_source_gap_events", contextSourceGapEvents);
        totals.put("context_attribute_policy_gap_events", contextAttributePolicyGapEvents);
        totals.put("context_block_gap_events", contextBlockGapEvents);
        totals.put("context_contract_gap_events", contextContractGapEvents);
        totals.put("context_sources_expanded_events", contextSourcesExpandedEvents);
        totals.put("context_attribute_policy_expanded_events", contextAttributePolicyExpandedEvents);
        totals.put("context_extra_attributes_expanded_events", contextExtraAttributesExpandedEvents);
        totals.put("context_secondary_details_expanded_events", contextSecondaryDetailsExpandedEvents);
        totals.put("context_secondary_details_usage_level", contextSecondaryDetailsUsageLevel);
        totals.put("context_secondary_details_top_section", contextSecondaryDetailsTopSection);
        totals.put("context_secondary_details_followup_required", contextSecondaryDetailsFollowupRequired);
        totals.put("context_secondary_details_management_review_required", contextSecondaryDetailsManagementReviewRequired);
        totals.put("context_secondary_details_summary", contextSecondaryDetailsSummary);
        totals.put("context_secondary_details_compaction_summary", contextSecondaryDetailsCompactionSummary);
        totals.put("context_extra_attributes_open_rate_pct", contextExtraAttributesOpenRatePct);
        totals.put("context_extra_attributes_usage_level", contextExtraAttributesUsageLevel);
        totals.put("context_extra_attributes_share_pct_of_secondary", contextExtraAttributesSharePctOfSecondary);
        totals.put("context_extra_attributes_compaction_candidate", contextExtraAttributesCompactionCandidate);
        totals.put("context_extra_attributes_summary", contextExtraAttributesSummary);
        totals.put("workspace_sla_policy_gap_events", workspaceSlaPolicyGapEvents);
        totals.put("workspace_parity_gap_events", workspaceParityGapEvents);
        totals.put("workspace_inline_navigation_events", workspaceInlineNavigationEvents);
        totals.put("manual_legacy_open_events", manualLegacyOpenEvents);
        totals.put("workspace_open_legacy_blocked_events", workspaceOpenLegacyBlockedEvents);
        totals.put("workspace_rollout_packet_viewed_events", workspaceRolloutPacketViewedEvents);
        totals.put("workspace_rollout_review_confirmed_events", workspaceRolloutReviewConfirmedEvents);
        totals.put("workspace_rollout_review_decision_go_events", workspaceRolloutReviewDecisionGoEvents);
        totals.put("workspace_rollout_review_decision_hold_events", workspaceRolloutReviewDecisionHoldEvents);
        totals.put("workspace_rollout_review_decision_rollback_events", workspaceRolloutReviewDecisionRollbackEvents);
        totals.put("workspace_rollout_review_incident_followup_linked_events", workspaceRolloutReviewIncidentFollowupLinkedEvents);
        totals.put("workspace_sla_policy_review_updated_events", workspaceSlaPolicyReviewUpdatedEvents);
        totals.put("workspace_sla_policy_decision_events", workspaceSlaPolicyDecisionEvents);
        totals.put("workspace_sla_policy_churn_ratio_pct", workspaceSlaPolicyChurnRatioPct);
        totals.put("workspace_sla_policy_decision_coverage_pct", workspaceSlaPolicyDecisionCoveragePct);
        totals.put("workspace_sla_policy_churn_level", workspaceSlaPolicyChurnLevel);
        totals.put("workspace_sla_policy_churn_followup_required", workspaceSlaPolicyChurnFollowupRequired);
        totals.put("workspace_sla_policy_churn_summary", workspaceSlaPolicyChurnSummary);
        totals.put("workspace_macro_governance_review_updated_events", workspaceMacroGovernanceReviewUpdatedEvents);
        totals.put("workspace_macro_external_catalog_policy_updated_events", workspaceMacroExternalCatalogPolicyUpdatedEvents);
        totals.put("workspace_macro_deprecation_policy_updated_events", workspaceMacroDeprecationPolicyUpdatedEvents);
        totals.put("workspace_macro_policy_update_events", workspaceMacroPolicyUpdateEvents);
        totals.put("context_profile_gap_rate", workspaceOpenEvents > 0 ? (double) contextProfileGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_profile_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextProfileGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_source_gap_rate", workspaceOpenEvents > 0 ? (double) contextSourceGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_source_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextSourceGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_attribute_policy_gap_rate", workspaceOpenEvents > 0 ? (double) contextAttributePolicyGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_attribute_policy_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextAttributePolicyGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_block_gap_rate", workspaceOpenEvents > 0 ? (double) contextBlockGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_block_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextBlockGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_contract_gap_rate", workspaceOpenEvents > 0 ? (double) contextContractGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_contract_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextContractGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_secondary_details_open_rate_pct", contextSecondaryDetailsOpenRatePct);
        totals.put("workspace_sla_policy_gap_rate", workspaceOpenEvents > 0 ? (double) workspaceSlaPolicyGapEvents / workspaceOpenEvents : 0d);
        totals.put("workspace_sla_policy_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) workspaceSlaPolicyGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("workspace_parity_gap_rate", workspaceOpenEvents > 0 ? (double) workspaceParityGapEvents / workspaceOpenEvents : 0d);
        totals.put("workspace_parity_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) workspaceParityGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("workspace_legacy_usage_policy_updated_events", workspaceLegacyUsagePolicyUpdatedEvents);
        totals.put("kpi_frt_recorded_events", frtRecordedEvents);
        totals.put("kpi_ttr_recorded_events", ttrRecordedEvents);
        totals.put("kpi_sla_breach_recorded_events", slaBreachRecordedEvents);
        totals.put("kpi_dialogs_per_shift_recorded_events", dialogsPerShiftRecordedEvents);
        totals.put("kpi_csat_recorded_events", csatRecordedEvents);
        totals.put("avg_frt_ms", avgFrtMs);
        totals.put("avg_ttr_ms", avgTtrMs);
        totals.put("avg_open_ms", avgOpenMs);
        return totals;
    }

    private Long weightedAverage(List<Map<String, Object>> rows, String weightKey, String avgKey) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        long weightSum = 0L;
        long weightedValueSum = 0L;
        for (Map<String, Object> row : rows) {
            long weight = toLong(row.get(weightKey));
            Long avg = extractNullableLong(row.get(avgKey));
            if (weight <= 0 || avg == null) {
                continue;
            }
            weightSum += weight;
            weightedValueSum += avg * weight;
        }
        return weightSum > 0 ? Math.round((double) weightedValueSum / weightSum) : null;
    }

    private long resolveWorkspaceOpenWeight(Map<String, Object> row) {
        return Math.max(toLong(row.get("events"))
                - toLong(row.get("render_errors"))
                - toLong(row.get("fallbacks"))
                - toLong(row.get("abandons")), 0L);
    }

    private Map<String, Object> buildPrimaryKpiOutcomeSignal(Map<String, Object> controlTotals,
                                                             Map<String, Object> testTotals) {
        Map<String, Object> signal = new LinkedHashMap<>();
        Map<String, Object> metrics = new LinkedHashMap<>();
        long minSamplesPerCohort = resolveOutcomeMinSamplesPerCohort();
        double frtRelativeRegression = resolveOutcomeRelativeRegressionThreshold(
                "workspace_rollout_kpi_outcome_frt_max_relative_regression",
                DEFAULT_KPI_OUTCOME_FRT_MAX_RELATIVE_REGRESSION);
        double ttrRelativeRegression = resolveOutcomeRelativeRegressionThreshold(
                "workspace_rollout_kpi_outcome_ttr_max_relative_regression",
                DEFAULT_KPI_OUTCOME_TTR_MAX_RELATIVE_REGRESSION);
        double slaBreachAbsoluteDelta = resolveOutcomeRateAbsoluteDeltaThreshold();
        double slaBreachRelativeMultiplier = resolveOutcomeRateRelativeMultiplierThreshold();

        Map<String, Object> frtMetric = buildLatencyMetricSignal(
                "frt",
                extractNullableLong(controlTotals.get("avg_frt_ms")),
                extractNullableLong(testTotals.get("avg_frt_ms")),
                toLong(controlTotals.get("kpi_frt_recorded_events")),
                toLong(testTotals.get("kpi_frt_recorded_events")),
                minSamplesPerCohort,
                frtRelativeRegression);
        Map<String, Object> ttrMetric = buildLatencyMetricSignal(
                "ttr",
                extractNullableLong(controlTotals.get("avg_ttr_ms")),
                extractNullableLong(testTotals.get("avg_ttr_ms")),
                toLong(controlTotals.get("kpi_ttr_recorded_events")),
                toLong(testTotals.get("kpi_ttr_recorded_events")),
                minSamplesPerCohort,
                ttrRelativeRegression);
        Map<String, Object> slaBreachMetric = buildRateMetricSignal(
                "sla_breach",
                toLong(controlTotals.get("kpi_sla_breach_recorded_events")),
                toLong(testTotals.get("kpi_sla_breach_recorded_events")),
                minSamplesPerCohort,
                slaBreachAbsoluteDelta,
                slaBreachRelativeMultiplier);

        metrics.put("frt", frtMetric);
        metrics.put("ttr", ttrMetric);
        metrics.put("sla_breach", slaBreachMetric);

        Set<String> requiredOutcomeKpis = resolveRequiredOutcomeKpis();
        List<Map<String, Object>> evaluatedMetrics = metrics.entrySet().stream()
                .filter(entry -> requiredOutcomeKpis.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::castObjectMap)
                .toList();
        boolean ready = !evaluatedMetrics.isEmpty()
                && evaluatedMetrics.stream().allMatch(metric -> toBoolean(metric.get("ready")));
        boolean hasRegression = evaluatedMetrics.stream().anyMatch(metric -> toBoolean(metric.get("regression")));

        signal.put("required_kpis", requiredOutcomeKpis);
        signal.put("evaluated_kpis", evaluatedMetrics.stream()
                .map(metric -> String.valueOf(metric.get("key")))
                .toList());
        signal.put("ready_for_decision", ready);
        signal.put("has_regression", hasRegression);
        signal.put("min_samples_per_cohort", minSamplesPerCohort);
        signal.put("thresholds", Map.of(
                "frt_max_relative_regression", frtRelativeRegression,
                "ttr_max_relative_regression", ttrRelativeRegression,
                "sla_breach_max_absolute_delta", slaBreachAbsoluteDelta,
                "sla_breach_max_relative_multiplier", slaBreachRelativeMultiplier
        ));
        signal.put("metrics", metrics);
        return signal;
    }

    private Map<String, Object> buildLatencyMetricSignal(String key,
                                                         Long controlAvgMs,
                                                         Long testAvgMs,
                                                         long controlSamples,
                                                         long testSamples,
                                                         long minSamplesPerCohort,
                                                         double maxRelativeRegression) {
        Map<String, Object> metric = new LinkedHashMap<>();
        boolean ready = controlSamples >= minSamplesPerCohort
                && testSamples >= minSamplesPerCohort
                && controlAvgMs != null
                && testAvgMs != null;
        Long deltaMs = ready ? testAvgMs - controlAvgMs : null;
        double relativeDelta = ready && controlAvgMs > 0 ? (double) deltaMs / controlAvgMs : 0d;
        boolean regression = ready && relativeDelta > maxRelativeRegression;
        metric.put("type", "latency_ms");
        metric.put("key", key);
        metric.put("control_value", controlAvgMs);
        metric.put("test_value", testAvgMs);
        metric.put("control_samples", controlSamples);
        metric.put("test_samples", testSamples);
        metric.put("min_samples_per_cohort", minSamplesPerCohort);
        metric.put("delta", deltaMs);
        metric.put("relative_delta", relativeDelta);
        metric.put("max_relative_regression", maxRelativeRegression);
        metric.put("ready", ready);
        metric.put("regression", regression);
        return metric;
    }

    private Map<String, Object> buildRateMetricSignal(String key,
                                                      long controlCount,
                                                      long testCount,
                                                      long minSamplesPerCohort,
                                                      double maxAbsoluteDelta,
                                                      double maxRelativeMultiplier) {
        Map<String, Object> metric = new LinkedHashMap<>();
        boolean ready = controlCount >= minSamplesPerCohort && testCount >= minSamplesPerCohort;
        double delta = testCount - controlCount;
        double multiplier = controlCount > 0 ? (double) testCount / controlCount : (testCount > 0 ? Double.POSITIVE_INFINITY : 1d);
        boolean regression = ready && (delta > maxAbsoluteDelta * Math.max(controlCount, 1) || multiplier > maxRelativeMultiplier);
        metric.put("type", "events_count");
        metric.put("key", key);
        metric.put("control_value", controlCount);
        metric.put("test_value", testCount);
        metric.put("min_samples_per_cohort", minSamplesPerCohort);
        metric.put("delta", delta);
        metric.put("multiplier", multiplier);
        metric.put("max_absolute_delta", maxAbsoluteDelta);
        metric.put("max_relative_multiplier", maxRelativeMultiplier);
        metric.put("ready", ready);
        metric.put("regression", regression);
        return metric;
    }

    private long resolveOutcomeMinSamplesPerCohort() {
        Object value = resolveDialogConfigValue("workspace_rollout_kpi_outcome_min_samples_per_cohort");
        if (value instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : DEFAULT_KPI_OUTCOME_MIN_SAMPLES_PER_COHORT;
        } catch (Exception ignored) {
            return DEFAULT_KPI_OUTCOME_MIN_SAMPLES_PER_COHORT;
        }
    }

    private double resolveOutcomeRelativeRegressionThreshold(String key, double fallback) {
        Object value = resolveDialogConfigValue(key);
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized > 0d && normalized <= 5d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed > 0d && parsed <= 5d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double resolveOutcomeRateAbsoluteDeltaThreshold() {
        Object value = resolveDialogConfigValue("workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta");
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized > 0d && normalized <= 1d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed > 0d && parsed <= 1d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_ABSOLUTE_DELTA;
            }
        }
        return DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_ABSOLUTE_DELTA;
    }

    private Set<String> resolveRequiredOutcomeKpis() {
        Object value = resolveDialogConfigValue("workspace_rollout_required_outcome_kpis");
        Set<String> resolved = parseKpiKeySet(value);
        return resolved.isEmpty() ? DEFAULT_REQUIRED_KPI_OUTCOME_KEYS : resolved;
    }

    private Set<String> parseKpiKeySet(Object rawValue) {
        if (rawValue == null) {
            return Set.of();
        }
        List<String> values = new ArrayList<>();
        if (rawValue instanceof List<?> list) {
            for (Object item : list) {
                values.add(String.valueOf(item));
            }
        } else {
            values.addAll(List.of(String.valueOf(rawValue).split(",")));
        }
        return values.stream()
                .map(value -> String.valueOf(value).trim().toLowerCase())
                .filter(StringUtils::hasText)
                .filter(DEFAULT_REQUIRED_KPI_OUTCOME_KEYS::contains)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }


    private double resolveWinnerMinOpenImprovementPercent() {
        Object value = resolveDialogConfigValue("workspace_rollout_winner_min_open_improvement");
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized >= 0d && normalized <= 0.5d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed >= 0d && parsed <= 0.5d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return DEFAULT_WORKSPACE_WINNER_MIN_OPEN_IMPROVEMENT;
            }
        }
        return DEFAULT_WORKSPACE_WINNER_MIN_OPEN_IMPROVEMENT;
    }

    private double resolveOutcomeRateRelativeMultiplierThreshold() {
        Object value = resolveDialogConfigValue("workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier");
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized >= 1d && normalized <= 10d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed >= 1d && parsed <= 10d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_RELATIVE_MULTIPLIER;
            }
        }
        return DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_RELATIVE_MULTIPLIER;
    }

    public Map<String, Object> buildWorkspaceTelemetryComparison(Map<String, Object> currentTotals,
                                                                  Map<String, Object> previousTotals) {
        long currentEvents = toLong(currentTotals.get("events"));
        long previousEvents = toLong(previousTotals.get("events"));

        double currentRenderRate = safeRate(toLong(currentTotals.get("render_errors")), currentEvents);
        double previousRenderRate = safeRate(toLong(previousTotals.get("render_errors")), previousEvents);
        double currentFallbackRate = safeRate(toLong(currentTotals.get("fallbacks")), currentEvents);
        double previousFallbackRate = safeRate(toLong(previousTotals.get("fallbacks")), previousEvents);
        double currentAbandonRate = safeRate(toLong(currentTotals.get("abandons")), currentEvents);
        double previousAbandonRate = safeRate(toLong(previousTotals.get("abandons")), previousEvents);
        double currentSlowOpenRate = safeRate(toLong(currentTotals.get("slow_open_events")), currentEvents);
        double previousSlowOpenRate = safeRate(toLong(previousTotals.get("slow_open_events")), previousEvents);

        Long currentAvgOpenMs = extractNullableLong(currentTotals.get("avg_open_ms"));
        Long previousAvgOpenMs = extractNullableLong(previousTotals.get("avg_open_ms"));
        Long avgOpenMsDelta = currentAvgOpenMs != null && previousAvgOpenMs != null
                ? currentAvgOpenMs - previousAvgOpenMs
                : null;

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("current_events", currentEvents);
        comparison.put("previous_events", previousEvents);
        comparison.put("render_error_rate_delta", currentRenderRate - previousRenderRate);
        comparison.put("fallback_rate_delta", currentFallbackRate - previousFallbackRate);
        comparison.put("abandon_rate_delta", currentAbandonRate - previousAbandonRate);
        comparison.put("slow_open_rate_delta", currentSlowOpenRate - previousSlowOpenRate);
        comparison.put("avg_open_ms_delta", avgOpenMsDelta);
        return comparison;
    }

    private Map<String, Object> buildPrimaryKpiSignal(Map<String, Object> controlTotals,
                                                      Map<String, Object> testTotals,
                                                      long controlEvents,
                                                      long testEvents) {
        List<String> requiredKpis = resolveRequiredPrimaryKpis();
        long minKpiEvents = resolveMinKpiEventsForDecision();
        double minCoverageRate = resolveMinKpiCoverageRateForDecision();
        Map<String, Object> metrics = new LinkedHashMap<>();
        boolean ready = true;
        for (String kpi : requiredKpis) {
            String key = "kpi_" + kpi + "_events";
            long control = toLong(controlTotals.get(key));
            long test = toLong(testTotals.get(key));
            long minObserved = Math.min(control, test);
            double controlCoverage = safeRate(control, controlEvents);
            double testCoverage = safeRate(test, testEvents);
            double minCoverage = Math.min(controlCoverage, testCoverage);
            boolean eventsReady = minObserved >= minKpiEvents;
            boolean coverageReady = minCoverage >= minCoverageRate;
            metrics.put(kpi, Map.ofEntries(
                    Map.entry("control", control),
                    Map.entry("test", test),
                    Map.entry("control_coverage", controlCoverage),
                    Map.entry("test_coverage", testCoverage),
                    Map.entry("min_coverage", minCoverage),
                    Map.entry("min_coverage_threshold", minCoverageRate),
                    Map.entry("min_observed", minObserved),
                    Map.entry("threshold", minKpiEvents),
                    Map.entry("events_ready", eventsReady),
                    Map.entry("coverage_ready", coverageReady),
                    Map.entry("ready", eventsReady && coverageReady)
            ));
            if (!eventsReady || !coverageReady) {
                ready = false;
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("required_kpis", requiredKpis);
        payload.put("min_events_per_cohort", minKpiEvents);
        payload.put("min_coverage_rate_per_cohort", minCoverageRate);
        payload.put("ready_for_decision", ready);
        payload.put("metrics", metrics);
        return payload;
    }

    private List<String> resolveRequiredPrimaryKpis() {
        Object value = resolveDialogConfigValue("workspace_rollout_required_primary_kpis");
        List<String> fromConfig = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String normalized = normalizeKpiKey(item);
                if (normalized != null) fromConfig.add(normalized);
            }
        } else if (value instanceof String csv) {
            for (String chunk : csv.split(",")) {
                String normalized = normalizeKpiKey(chunk);
                if (normalized != null) fromConfig.add(normalized);
            }
        }
        List<String> unique = fromConfig.stream().distinct().toList();
        return unique.isEmpty() ? DEFAULT_REQUIRED_PRIMARY_KPIS : unique;
    }

    private long resolveMinKpiEventsForDecision() {
        Object value = resolveDialogConfigValue("workspace_rollout_min_kpi_events");
        if (value instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : DEFAULT_MIN_KPI_EVENTS_FOR_DECISION;
        } catch (Exception ignored) {
            return DEFAULT_MIN_KPI_EVENTS_FOR_DECISION;
        }
    }


    private double resolveMinKpiCoverageRateForDecision() {
        Object value = resolveDialogConfigValue("workspace_rollout_min_kpi_coverage_rate");
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized > 0d && normalized <= 1d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed > 0d && parsed <= 1d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return DEFAULT_MIN_KPI_COVERAGE_RATE_FOR_DECISION;
            }
        }
        return DEFAULT_MIN_KPI_COVERAGE_RATE_FOR_DECISION;
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

    private String normalizeKpiKey(Object raw) {
        String value = raw == null ? "" : String.valueOf(raw).trim().toLowerCase();
        if (value.isBlank()) return null;
        return value.replace('-', '_');
    }

    public Map<String, Object> buildWorkspaceGuardrails(Map<String, Object> totals,
                                                          Map<String, Object> previousTotals,
                                                          List<Map<String, Object>> cohortRows,
                                                          List<Map<String, Object>> shiftRows,
                                                          List<Map<String, Object>> teamRows,
                                                          Map<String, Object> telemetryConfig) {
        long events = Math.max(1L, toLong(totals.get("events")));
        long renderErrors = toLong(totals.get("render_errors"));
        long fallbacks = toLong(totals.get("fallbacks"));
        long abandons = toLong(totals.get("abandons"));
        long slowOpenEvents = toLong(totals.get("slow_open_events"));

        double renderErrorRate = (double) renderErrors / events;
        double fallbackRate = (double) fallbacks / events;
        double abandonRate = (double) abandons / events;
        double slowOpenRate = (double) slowOpenEvents / events;

        double renderErrorThreshold = resolveDoubleConfig(telemetryConfig, "guardrail_render_error_rate", DEFAULT_GUARDRAIL_RENDER_ERROR_RATE, 0.0001d, 1d);
        double fallbackThreshold = resolveDoubleConfig(telemetryConfig, "guardrail_fallback_rate", DEFAULT_GUARDRAIL_FALLBACK_RATE, 0.0001d, 1d);
        double abandonThreshold = resolveDoubleConfig(telemetryConfig, "guardrail_abandon_rate", DEFAULT_GUARDRAIL_ABANDON_RATE, 0.0001d, 1d);
        double slowOpenThreshold = resolveDoubleConfig(telemetryConfig, "guardrail_slow_open_rate", DEFAULT_GUARDRAIL_SLOW_OPEN_RATE, 0.0001d, 1d);
        int minDimensionEvents = (int) resolveLongConfig(telemetryConfig, "dimension_min_events", DEFAULT_DIMENSION_MIN_EVENTS, 5, 1000);

        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("render_error", renderErrorRate);
        rates.put("fallback", fallbackRate);
        rates.put("abandon", abandonRate);
        rates.put("slow_open", slowOpenRate);
        rates.put("threshold_render_error", renderErrorThreshold);
        rates.put("threshold_fallback", fallbackThreshold);
        rates.put("threshold_abandon", abandonThreshold);
        rates.put("threshold_slow_open", slowOpenThreshold);

        List<Map<String, Object>> alerts = new ArrayList<>();
        appendGuardrailAlert(alerts,
                "render_error",
                "Доля workspace_render_error превышает SLO 1%.",
                renderErrorRate,
                renderErrorThreshold,
                "below_or_equal");
        appendGuardrailAlert(alerts,
                "fallback",
                "Доля fallback в legacy превышает SLO 3%.",
                fallbackRate,
                fallbackThreshold,
                "below_or_equal");
        appendGuardrailAlert(alerts,
                "abandon",
                "Доля abandon в workspace превышает рабочий порог 10%.",
                abandonRate,
                abandonThreshold,
                "below_or_equal");
        appendGuardrailAlert(alerts,
                "slow_open",
                "Доля медленных workspace_open_ms (>2000ms) превышает рабочий порог 5%.",
                slowOpenRate,
                slowOpenThreshold,
                "below_or_equal");
        appendRegressionGuardrailAlerts(alerts, totals, previousTotals);
        appendDimensionGuardrailAlerts(alerts, cohortRows, "cohort", "experiment_cohort", minDimensionEvents);
        appendDimensionGuardrailAlerts(alerts, shiftRows, "shift", "shift", minDimensionEvents);
        appendDimensionGuardrailAlerts(alerts, teamRows, "team", "team", minDimensionEvents);

        Map<String, Object> guardrails = new LinkedHashMap<>();
        guardrails.put("status", alerts.isEmpty() ? "ok" : "attention");
        guardrails.put("rates", rates);
        guardrails.put("alerts", alerts);
        guardrails.put("dimension_min_events", minDimensionEvents);
        return guardrails;
    }

    public Map<String, Object> resolveWorkspaceTelemetryConfig() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (dialogConfigRaw instanceof Map<?, ?> dialogConfig) {
            return castObjectMap(dialogConfig);
        }
        return Map.of();
    }

    private double resolveDoubleConfig(Map<String, Object> source,
                                       String key,
                                       double fallback,
                                       double min,
                                       double max) {
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        Object raw = source.get(key);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (Double.isFinite(value) && value >= min && value <= max) {
                return value;
            }
            return fallback;
        }
        if (raw instanceof String stringValue) {
            try {
                double value = Double.parseDouble(stringValue.trim());
                if (Double.isFinite(value) && value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private long resolveLongConfig(Map<String, Object> source,
                                   String key,
                                   long fallback,
                                   long min,
                                   long max) {
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        Object raw = source.get(key);
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (value >= min && value <= max) {
                return value;
            }
            return fallback;
        }
        if (raw instanceof String stringValue) {
            try {
                long value = Long.parseLong(stringValue.trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean resolveBooleanConfig(Map<String, Object> source,
                                         String key,
                                         boolean fallback) {
        if (source == null || source.isEmpty() || !source.containsKey(key)) {
            return fallback;
        }
        Object raw = source.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof String stringValue && !StringUtils.hasText(stringValue)) {
            return fallback;
        }
        return toBoolean(raw);
    }

    private void appendRegressionGuardrailAlerts(List<Map<String, Object>> alerts,
                                                 Map<String, Object> currentTotals,
                                                 Map<String, Object> previousTotals) {
        long currentEvents = toLong(currentTotals.get("events"));
        long previousEvents = toLong(previousTotals.get("events"));
        if (currentEvents < 20 || previousEvents < 20) {
            return;
        }

        appendRegressionAlert(
                alerts,
                "render_error",
                "Регрессия render_error относительно предыдущего окна.",
                safeRate(toLong(currentTotals.get("render_errors")), currentEvents),
                safeRate(toLong(previousTotals.get("render_errors")), previousEvents),
                0.005d,
                1.35d,
                currentEvents,
                previousEvents);
        appendRegressionAlert(
                alerts,
                "fallback",
                "Регрессия fallback относительно предыдущего окна.",
                safeRate(toLong(currentTotals.get("fallbacks")), currentEvents),
                safeRate(toLong(previousTotals.get("fallbacks")), previousEvents),
                0.01d,
                1.35d,
                currentEvents,
                previousEvents);
        appendRegressionAlert(
                alerts,
                "abandon",
                "Регрессия abandon относительно предыдущего окна.",
                safeRate(toLong(currentTotals.get("abandons")), currentEvents),
                safeRate(toLong(previousTotals.get("abandons")), previousEvents),
                0.02d,
                1.25d,
                currentEvents,
                previousEvents);
        appendRegressionAlert(
                alerts,
                "slow_open",
                "Регрессия slow_open относительно предыдущего окна.",
                safeRate(toLong(currentTotals.get("slow_open_events")), currentEvents),
                safeRate(toLong(previousTotals.get("slow_open_events")), previousEvents),
                0.015d,
                1.3d,
                currentEvents,
                previousEvents);
    }

    private void appendRegressionAlert(List<Map<String, Object>> alerts,
                                       String metric,
                                       String message,
                                       double current,
                                       double previous,
                                       double minAbsoluteDelta,
                                       double minRelativeMultiplier,
                                       long currentEvents,
                                       long previousEvents) {
        double delta = current - previous;
        if (delta <= minAbsoluteDelta) {
            return;
        }
        double safeBase = Math.max(previous, 0.0001d);
        double multiplier = current / safeBase;
        if (multiplier <= minRelativeMultiplier) {
            return;
        }
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("metric", metric);
        alert.put("message", message);
        alert.put("value", current);
        alert.put("previous_value", previous);
        alert.put("delta", delta);
        alert.put("threshold", minAbsoluteDelta);
        alert.put("expected", "regression_delta_below_threshold");
        alert.put("scope", "global");
        alert.put("segment", "all");
        alert.put("events", currentEvents);
        alert.put("previous_events", previousEvents);
        alerts.add(alert);
    }

    private void appendDimensionGuardrailAlerts(List<Map<String, Object>> alerts,
                                                List<Map<String, Object>> rows,
                                                String scope,
                                                String field,
                                                int minEvents) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            long events = toLong(row.get("events"));
            if (events < minEvents) {
                continue;
            }
            double renderErrorRate = safeRate(toLong(row.get("render_errors")), events);
            double fallbackRate = safeRate(toLong(row.get("fallbacks")), events);
            double slowOpenRate = safeRate(toLong(row.get("slow_open_events")), events);
            double abandonRate = safeRate(toLong(row.get("abandons")), events);
            String dimensionValue = String.valueOf(row.getOrDefault(field, "unknown"));

            appendGuardrailAlert(alerts,
                    "render_error",
                    "Отклонение render_error в срезе " + scope + ": " + dimensionValue + ".",
                    renderErrorRate,
                    0.01d,
                    "below_or_equal",
                    scope,
                    dimensionValue,
                    events);
            appendGuardrailAlert(alerts,
                    "fallback",
                    "Отклонение fallback в срезе " + scope + ": " + dimensionValue + ".",
                    fallbackRate,
                    0.03d,
                    "below_or_equal",
                    scope,
                    dimensionValue,
                    events);
            appendGuardrailAlert(alerts,
                    "abandon",
                    "Отклонение abandon в срезе " + scope + ": " + dimensionValue + ".",
                    abandonRate,
                    0.10d,
                    "below_or_equal",
                    scope,
                    dimensionValue,
                    events);
            appendGuardrailAlert(alerts,
                    "slow_open",
                    "Отклонение slow_open в срезе " + scope + ": " + dimensionValue + ".",
                    slowOpenRate,
                    0.05d,
                    "below_or_equal",
                    scope,
                    dimensionValue,
                    events);
        }
    }

    private double safeRate(long value, long events) {
        if (events <= 0) {
            return 0d;
        }
        return (double) value / events;
    }

    private void appendGuardrailAlert(List<Map<String, Object>> alerts,
                                      String metric,
                                      String message,
                                      double value,
                                      double threshold,
                                      String expected) {
        if (value <= threshold) {
            return;
        }
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("metric", metric);
        alert.put("message", message);
        alert.put("value", value);
        alert.put("threshold", threshold);
        alert.put("expected", expected);
        alerts.add(alert);
    }

    private void appendGuardrailAlert(List<Map<String, Object>> alerts,
                                      String metric,
                                      String message,
                                      double value,
                                      double threshold,
                                      String expected,
                                      String scope,
                                      String segment,
                                      long events) {
        if (value <= threshold) {
            return;
        }
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("metric", metric);
        alert.put("message", message);
        alert.put("value", value);
        alert.put("threshold", threshold);
        alert.put("expected", expected);
        alert.put("scope", scope);
        alert.put("segment", segment);
        alert.put("events", events);
        alerts.add(alert);
    }

    private Long extractNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
