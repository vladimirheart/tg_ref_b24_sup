package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogWorkspaceTelemetrySummaryAssemblerService {

    private final DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService;
    private final DialogWorkspaceTelemetryAnalyticsService dialogWorkspaceTelemetryAnalyticsService;
    private final DialogWorkspaceRolloutAssessmentService dialogWorkspaceRolloutAssessmentService;
    private final DialogWorkspaceRolloutGovernanceService dialogWorkspaceRolloutGovernanceService;

    public DialogWorkspaceTelemetrySummaryAssemblerService(DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService,
                                                          DialogWorkspaceTelemetryAnalyticsService dialogWorkspaceTelemetryAnalyticsService,
                                                          DialogWorkspaceRolloutAssessmentService dialogWorkspaceRolloutAssessmentService,
                                                          DialogWorkspaceRolloutGovernanceService dialogWorkspaceRolloutGovernanceService) {
        this.dialogWorkspaceTelemetryDataService = dialogWorkspaceTelemetryDataService;
        this.dialogWorkspaceTelemetryAnalyticsService = dialogWorkspaceTelemetryAnalyticsService;
        this.dialogWorkspaceRolloutAssessmentService = dialogWorkspaceRolloutAssessmentService;
        this.dialogWorkspaceRolloutGovernanceService = dialogWorkspaceRolloutGovernanceService;
    }

    public Map<String, Object> loadSummary(int days, String experimentName) {
        int windowDays = Math.max(1, Math.min(days, 30));
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
        Instant previousWindowEnd = windowStart;
        Instant previousWindowStart = previousWindowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
        return assemble(windowDays, experimentName, windowStart, windowEnd, previousWindowStart, previousWindowEnd);
    }

    public Map<String, Object> loadSummary(int days,
                                           String experimentName,
                                           Instant fromUtc,
                                           Instant toUtc) {
        Instant resolvedEnd = toUtc != null ? toUtc : Instant.now();
        int fallbackWindowDays = Math.max(1, Math.min(days, 30));
        Instant resolvedStart = fromUtc != null
                ? fromUtc
                : resolvedEnd.minusSeconds(fallbackWindowDays * 24L * 60L * 60L);
        if (!resolvedStart.isBefore(resolvedEnd)) {
            resolvedStart = resolvedEnd.minusSeconds(fallbackWindowDays * 24L * 60L * 60L);
        }
        long rangeSeconds = Math.max(1L, Duration.between(resolvedStart, resolvedEnd).getSeconds());
        long windowDaysRaw = Math.max(1L, (long) Math.ceil((double) rangeSeconds / (24d * 60d * 60d)));
        int windowDays = (int) Math.max(1L, Math.min(30L, windowDaysRaw));

        Instant previousWindowEnd = resolvedStart;
        Instant previousWindowStart = previousWindowEnd.minusSeconds(rangeSeconds);
        return assemble(windowDays, experimentName, resolvedStart, resolvedEnd, previousWindowStart, previousWindowEnd);
    }

    private Map<String, Object> assemble(int windowDays,
                                         String experimentName,
                                         Instant windowStart,
                                         Instant windowEnd,
                                         Instant previousWindowStart,
                                         Instant previousWindowEnd) {
        Map<String, Object> workspaceTelemetryConfig = dialogWorkspaceTelemetryAnalyticsService.resolveWorkspaceTelemetryConfig();
        List<Map<String, Object>> rows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(windowStart, windowEnd, experimentName);
        List<Map<String, Object>> previousRows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(previousWindowStart, previousWindowEnd, experimentName);
        List<Map<String, Object>> shiftRows = dialogWorkspaceTelemetryDataService.aggregateWorkspaceTelemetryRows(rows, "shift");
        List<Map<String, Object>> teamRows = dialogWorkspaceTelemetryDataService.aggregateWorkspaceTelemetryRows(rows, "team");
        Map<String, Object> totals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(rows);
        Map<String, Object> previousTotals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(previousRows);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", windowDays);
        payload.put("window_from_utc", windowStart.toString());
        payload.put("window_to_utc", windowEnd.toString());
        payload.put("generated_at", Instant.now().toString());
        payload.put("totals", totals);
        payload.put("previous_totals", previousTotals);
        payload.put("period_comparison", dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceTelemetryComparison(totals, previousTotals));
        Map<String, Object> cohortComparison = dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceCohortComparison(rows, workspaceTelemetryConfig);
        payload.put("cohort_comparison", cohortComparison);
        payload.put("rows", rows);
        payload.put("by_shift", shiftRows);
        payload.put("by_team", teamRows);
        payload.put("gap_breakdown", dialogWorkspaceTelemetryDataService.loadWorkspaceGapBreakdown(windowStart, windowEnd, experimentName));
        Map<String, Object> guardrails = dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceGuardrails(
                totals, previousTotals, rows, shiftRows, teamRows, workspaceTelemetryConfig);
        payload.put("guardrails", guardrails);
        Map<String, Object> rolloutDecision = dialogWorkspaceRolloutAssessmentService.buildRolloutDecision(cohortComparison, guardrails);
        payload.put("rollout_decision", rolloutDecision);
        Map<String, Object> rolloutScorecard = dialogWorkspaceRolloutAssessmentService.buildRolloutScorecard(totals, cohortComparison, guardrails, rolloutDecision);
        payload.put("rollout_scorecard", rolloutScorecard);
        payload.put("rollout_packet", dialogWorkspaceRolloutGovernanceService.buildWorkspaceRolloutPacket(
                totals,
                guardrails,
                rolloutDecision,
                rolloutScorecard,
                payload.get("gap_breakdown"),
                windowDays,
                experimentName));
        return payload;
    }
}
