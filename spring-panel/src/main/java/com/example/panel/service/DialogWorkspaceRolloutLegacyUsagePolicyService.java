package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogWorkspaceRolloutLegacyUsagePolicyService {

    private final DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService;
    private final DialogWorkspaceTelemetryAnalyticsService dialogWorkspaceTelemetryAnalyticsService;
    private final DialogWorkspaceRolloutGovernanceConfigService configService;

    public DialogWorkspaceRolloutLegacyUsagePolicyService(DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService,
                                                          DialogWorkspaceTelemetryAnalyticsService dialogWorkspaceTelemetryAnalyticsService,
                                                          DialogWorkspaceRolloutGovernanceConfigService configService) {
        this.dialogWorkspaceTelemetryDataService = dialogWorkspaceTelemetryDataService;
        this.dialogWorkspaceTelemetryAnalyticsService = dialogWorkspaceTelemetryAnalyticsService;
        this.configService = configService;
    }

    public DialogWorkspaceRolloutSectionResult buildLegacyUsagePolicy(DialogWorkspaceRolloutGovernanceConfig config,
                                                                      Map<String, Object> totals,
                                                                      int windowDays,
                                                                      String experimentName) {
        OffsetDateTime reviewedAt = configService.parseReviewTimestamp(config.legacyUsageReviewedAtRaw());
        boolean reviewTimestampInvalid = StringUtils.hasText(configService.normalizeNullString(config.legacyUsageReviewedAtRaw()))
                && reviewedAt == null;
        boolean reviewPresent = reviewedAt != null && StringUtils.hasText(config.legacyUsageReviewedBy());
        boolean reviewFresh = false;
        long reviewAgeHours = -1L;
        if (reviewedAt != null) {
            reviewAgeHours = Math.max(0, Duration.between(reviewedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            reviewFresh = reviewAgeHours <= config.legacyUsageReviewTtlHours();
        }
        long policyUpdatedEvents = configService.toLong(totals.get("workspace_legacy_usage_policy_updated_events"));
        long manualLegacyOpenEvents = configService.toLong(totals.get("manual_legacy_open_events"));
        long manualLegacyBlockedEvents = configService.toLong(totals.get("workspace_open_legacy_blocked_events"));
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minusSeconds(Math.max(1, windowDays) * 24L * 60L * 60L);
        Instant previousWindowEnd = windowStart;
        Instant previousWindowStart = previousWindowEnd.minusSeconds(Math.max(1, windowDays) * 24L * 60L * 60L);

        List<Map<String, Object>> manualReasonBreakdown = dialogWorkspaceTelemetryDataService.loadWorkspaceEventReasonBreakdown(
                "workspace_open_legacy_manual", windowStart, windowEnd, experimentName, 5);
        List<Map<String, Object>> blockedReasonBreakdown = dialogWorkspaceTelemetryDataService.loadWorkspaceEventReasonBreakdown(
                "workspace_open_legacy_blocked", windowStart, windowEnd, experimentName, 5);
        long unknownManualReasonEvents = manualReasonBreakdown.stream()
                .filter(row -> {
                    String reason = configService.normalizeNullString(String.valueOf(row.getOrDefault("reason", "")));
                    return !StringUtils.hasText(reason)
                            || (config.legacyManualReasonCatalogRequired() && !config.legacyManualAllowedReasons().contains(reason));
                })
                .mapToLong(row -> configService.toLong(row.get("events")))
                .sum();

        List<Map<String, Object>> previousRows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(
                previousWindowStart, previousWindowEnd, experimentName);
        Map<String, Object> previousTotals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(previousRows);
        long workspaceOpenEvents = configService.toLong(totals.get("workspace_open_events"));
        long previousWorkspaceOpenEvents = configService.toLong(previousTotals.get("workspace_open_events"));
        long previousManualLegacyOpenEvents = configService.toLong(previousTotals.get("manual_legacy_open_events"));
        long previousManualLegacyBlockedEvents = configService.toLong(previousTotals.get("workspace_open_legacy_blocked_events"));

        double previousManualLegacyShareRatio = previousWorkspaceOpenEvents > 0
                ? (double) previousManualLegacyOpenEvents / previousWorkspaceOpenEvents
                : 0d;
        double previousManualLegacyBlockedShareRatio = previousWorkspaceOpenEvents > 0
                ? (double) previousManualLegacyBlockedEvents / previousWorkspaceOpenEvents
                : 0d;
        double manualLegacyShareRatio = workspaceOpenEvents > 0 ? (double) manualLegacyOpenEvents / workspaceOpenEvents : 0d;
        double manualLegacyBlockedShareRatio = workspaceOpenEvents > 0 ? (double) manualLegacyBlockedEvents / workspaceOpenEvents : 0d;
        double manualLegacyShareDeltaPct = (manualLegacyShareRatio - previousManualLegacyShareRatio) * 100d;
        double manualLegacyBlockedShareDeltaPct = (manualLegacyBlockedShareRatio - previousManualLegacyBlockedShareRatio) * 100d;

        boolean thresholdConfigured = config.legacyUsageMaxSharePct() != null;
        double thresholdShare = thresholdConfigured ? config.legacyUsageMaxSharePct() / 100d : 1d;
        boolean thresholdReady = !thresholdConfigured || manualLegacyShareRatio <= thresholdShare;
        boolean minVolumeConfigured = config.legacyUsageMinWorkspaceOpenEvents() != null;
        boolean volumeReady = !minVolumeConfigured || workspaceOpenEvents >= config.legacyUsageMinWorkspaceOpenEvents();
        boolean shareDeltaConfigured = config.legacyUsageMaxShareDeltaPct() != null;
        boolean trendReady = !shareDeltaConfigured || manualLegacyShareDeltaPct <= config.legacyUsageMaxShareDeltaPct();
        boolean blockedDeltaConfigured = config.legacyUsageMaxBlockedShareDeltaPct() != null;
        boolean blockedTrendReady = !blockedDeltaConfigured || manualLegacyBlockedShareDeltaPct <= config.legacyUsageMaxBlockedShareDeltaPct();

        List<String> blockedReasonsTopKeys = blockedReasonBreakdown.stream()
                .limit(config.legacyBlockedReasonsTopN())
                .map(row -> configService.normalizeNullString(String.valueOf(row.getOrDefault("reason", "unspecified"))))
                .map(reason -> StringUtils.hasText(reason) ? reason.toLowerCase(java.util.Locale.ROOT) : "unspecified")
                .distinct()
                .toList();
        List<String> blockedReasonsMissing = blockedReasonsTopKeys.stream()
                .filter(reason -> !config.legacyBlockedReasonsReviewed().contains(reason))
                .toList();
        boolean blockedReviewConfigured = !config.legacyBlockedReasonsReviewed().isEmpty()
                || StringUtils.hasText(config.legacyBlockedReasonsFollowup());
        boolean blockedReviewNeeded = config.legacyBlockedReasonsReviewRequired() && manualLegacyBlockedEvents > 0;
        boolean blockedFollowupPresent = StringUtils.hasText(config.legacyBlockedReasonsFollowup());
        boolean blockedReviewReady = !blockedReviewNeeded || (blockedReasonsMissing.isEmpty() && blockedFollowupPresent);
        boolean decisionPresent = StringUtils.hasText(config.legacyUsageDecision());

        boolean enabled = thresholdConfigured
                || minVolumeConfigured
                || shareDeltaConfigured
                || blockedDeltaConfigured
                || config.legacyBlockedReasonsReviewRequired()
                || blockedReviewConfigured
                || config.legacyUsageDecisionRequired()
                || reviewPresent
                || StringUtils.hasText(config.legacyUsageReviewNote());
        boolean ready = !enabled
                || (reviewPresent
                && reviewFresh
                && !reviewTimestampInvalid
                && thresholdReady
                && volumeReady
                && trendReady
                && blockedTrendReady
                && blockedReviewReady
                && (!config.legacyUsageDecisionRequired() || decisionPresent));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("ready", ready);
        payload.put("reviewed_by", config.legacyUsageReviewedBy() == null ? "" : config.legacyUsageReviewedBy());
        payload.put("reviewed_at", reviewedAt != null ? reviewedAt.toString() : "");
        payload.put("review_note", config.legacyUsageReviewNote() == null ? "" : config.legacyUsageReviewNote());
        payload.put("review_ttl_hours", config.legacyUsageReviewTtlHours());
        payload.put("review_age_hours", reviewAgeHours);
        payload.put("review_timestamp_invalid", reviewTimestampInvalid);
        payload.put("manual_legacy_open_events", manualLegacyOpenEvents);
        payload.put("manual_legacy_blocked_events", manualLegacyBlockedEvents);
        payload.put("manual_legacy_reasons_top", manualReasonBreakdown);
        payload.put("manual_legacy_blocked_reasons_top", blockedReasonBreakdown);
        payload.put("allowed_reasons", config.legacyManualAllowedReasons());
        payload.put("reason_catalog_required", config.legacyManualReasonCatalogRequired());
        payload.put("unknown_manual_reason_events", unknownManualReasonEvents);
        payload.put("blocked_reasons_review_required", config.legacyBlockedReasonsReviewRequired());
        payload.put("blocked_reasons_top_n", config.legacyBlockedReasonsTopN());
        payload.put("blocked_reasons_reviewed", config.legacyBlockedReasonsReviewed());
        payload.put("blocked_reasons_followup", config.legacyBlockedReasonsFollowup() == null ? "" : config.legacyBlockedReasonsFollowup());
        payload.put("blocked_reasons_missing", blockedReasonsMissing);
        payload.put("blocked_reasons_review_ready", blockedReviewReady);
        payload.put("workspace_open_events", workspaceOpenEvents);
        payload.put("manual_legacy_share_pct", Math.round(manualLegacyShareRatio * 1000d) / 10d);
        payload.put("max_manual_legacy_share_pct", config.legacyUsageMaxSharePct());
        payload.put("threshold_ready", thresholdReady);
        payload.put("min_workspace_open_events", config.legacyUsageMinWorkspaceOpenEvents());
        payload.put("volume_ready", volumeReady);
        payload.put("max_manual_legacy_share_delta_pct", config.legacyUsageMaxShareDeltaPct());
        payload.put("previous_window_manual_legacy_share_pct", Math.round(previousManualLegacyShareRatio * 1000d) / 10d);
        payload.put("manual_legacy_share_delta_pct", Math.round(manualLegacyShareDeltaPct * 10d) / 10d);
        payload.put("trend_ready", trendReady);
        payload.put("max_manual_legacy_blocked_share_delta_pct", config.legacyUsageMaxBlockedShareDeltaPct());
        payload.put("previous_window_manual_legacy_blocked_share_pct", Math.round(previousManualLegacyBlockedShareRatio * 1000d) / 10d);
        payload.put("manual_legacy_blocked_share_pct", Math.round(manualLegacyBlockedShareRatio * 1000d) / 10d);
        payload.put("manual_legacy_blocked_share_delta_pct", Math.round(manualLegacyBlockedShareDeltaPct * 10d) / 10d);
        payload.put("blocked_trend_ready", blockedTrendReady);
        payload.put("decision_required", config.legacyUsageDecisionRequired());
        payload.put("decision", config.legacyUsageDecision() == null ? "" : config.legacyUsageDecision());
        payload.put("policy_updated_events_in_window", policyUpdatedEvents);

        String currentValue = !enabled
                ? "not required"
                : reviewTimestampInvalid
                ? "invalid_utc"
                : "manual_legacy_share=%.1f%% (events=%d/%d)%s%s%s%s%s".formatted(
                manualLegacyShareRatio * 100d,
                manualLegacyOpenEvents,
                workspaceOpenEvents,
                thresholdConfigured ? ", max=%d%%".formatted(config.legacyUsageMaxSharePct()) : "",
                shareDeltaConfigured ? ", delta=%.1fpp (max +%dpp)".formatted(manualLegacyShareDeltaPct, config.legacyUsageMaxShareDeltaPct()) : "",
                blockedDeltaConfigured ? ", blocked_delta=%.1fpp (max +%dpp)".formatted(manualLegacyBlockedShareDeltaPct, config.legacyUsageMaxBlockedShareDeltaPct()) : "",
                blockedReviewNeeded ? ", blocked_review=%d/%d".formatted(
                        blockedReasonsTopKeys.size() - blockedReasonsMissing.size(), blockedReasonsTopKeys.size()) : "",
                decisionPresent ? ", decision=%s".formatted(config.legacyUsageDecision()) : "");
        String expectedValue = enabled
                ? "review <= %d h UTC%s%s%s%s%s%s".formatted(
                config.legacyUsageReviewTtlHours(),
                thresholdConfigured ? ", manual share <= %d%%".formatted(config.legacyUsageMaxSharePct()) : "",
                minVolumeConfigured ? ", workspace opens >= %d".formatted(config.legacyUsageMinWorkspaceOpenEvents()) : "",
                shareDeltaConfigured ? ", share delta <= +%dpp vs previous window".formatted(config.legacyUsageMaxShareDeltaPct()) : "",
                blockedDeltaConfigured ? ", blocked share delta <= +%dpp vs previous window".formatted(config.legacyUsageMaxBlockedShareDeltaPct()) : "",
                config.legacyBlockedReasonsReviewRequired() ? ", blocked top-%d reasons reviewed + follow-up".formatted(config.legacyBlockedReasonsTopN()) : "",
                config.legacyUsageDecisionRequired() ? ", decision required" : "")
                : "optional";
        String note = configService.firstNonBlank(
                config.legacyUsageReviewNote(),
                blockedReviewNeeded
                        ? "blocked_missing=%s%s".formatted(
                        blockedReasonsMissing.isEmpty() ? "none" : String.join(", ", blockedReasonsMissing),
                        blockedFollowupPresent ? "; followup=linked" : "; followup=missing")
                        : (reviewPresent ? "reviewed_by=%s; age_hours=%d".formatted(config.legacyUsageReviewedBy(), reviewAgeHours) : ""));

        return new DialogWorkspaceRolloutSectionResult(
                "legacy_usage_policy",
                "workspace",
                "Legacy manual-open policy",
                !enabled ? "off" : (ready ? "ok" : "hold"),
                enabled && !ready,
                "Переход к primary-flow требует контролировать долю manual legacy-open в UTC-окне и зафиксировать review-решение.",
                currentValue,
                expectedValue,
                reviewedAt != null ? reviewedAt.toString() : "",
                note,
                payload
        );
    }
}
