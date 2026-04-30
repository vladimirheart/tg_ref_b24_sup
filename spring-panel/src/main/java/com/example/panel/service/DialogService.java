package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogPreviousHistoryPage;
import com.example.panel.model.dialog.DialogSummary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DialogService {

    private final DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService;
    private final DialogWorkspaceTelemetryAnalyticsService dialogWorkspaceTelemetryAnalyticsService;
    private final DialogWorkspaceRolloutAssessmentService dialogWorkspaceRolloutAssessmentService;
    private final DialogWorkspaceRolloutGovernanceService dialogWorkspaceRolloutGovernanceService;
    private final DialogMacroGovernanceAuditService dialogMacroGovernanceAuditService;
    private final DialogLookupReadService dialogLookupReadService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogClientContextReadService dialogClientContextReadService;
    private final DialogConversationReadService dialogConversationReadService;
    private final DialogDetailsReadService dialogDetailsReadService;
    private final DialogAuditService dialogAuditService;
    private final DialogTicketLifecycleService dialogTicketLifecycleService;

    public DialogService(DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService,
                         DialogWorkspaceTelemetryAnalyticsService dialogWorkspaceTelemetryAnalyticsService,
                         DialogWorkspaceRolloutAssessmentService dialogWorkspaceRolloutAssessmentService,
                         DialogWorkspaceRolloutGovernanceService dialogWorkspaceRolloutGovernanceService,
                         DialogMacroGovernanceAuditService dialogMacroGovernanceAuditService,
                         DialogLookupReadService dialogLookupReadService,
                         DialogResponsibilityService dialogResponsibilityService,
                         DialogClientContextReadService dialogClientContextReadService,
                         DialogConversationReadService dialogConversationReadService,
                         DialogDetailsReadService dialogDetailsReadService,
                         DialogAuditService dialogAuditService,
                         DialogTicketLifecycleService dialogTicketLifecycleService) {
        this.dialogWorkspaceTelemetryDataService = dialogWorkspaceTelemetryDataService;
        this.dialogWorkspaceTelemetryAnalyticsService = dialogWorkspaceTelemetryAnalyticsService;
        this.dialogWorkspaceRolloutAssessmentService = dialogWorkspaceRolloutAssessmentService;
        this.dialogWorkspaceRolloutGovernanceService = dialogWorkspaceRolloutGovernanceService;
        this.dialogMacroGovernanceAuditService = dialogMacroGovernanceAuditService;
        this.dialogLookupReadService = dialogLookupReadService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogClientContextReadService = dialogClientContextReadService;
        this.dialogConversationReadService = dialogConversationReadService;
        this.dialogDetailsReadService = dialogDetailsReadService;
        this.dialogAuditService = dialogAuditService;
        this.dialogTicketLifecycleService = dialogTicketLifecycleService;
    }

    public DialogSummary loadSummary() {
        return dialogLookupReadService.loadSummary();
    }

    public List<DialogListItem> loadDialogs(String currentOperator) {
        return dialogLookupReadService.loadDialogs(currentOperator);
    }

    public Optional<DialogListItem> findDialog(String ticketId, String operator) {
        return dialogLookupReadService.findDialog(ticketId, operator);
    }

    public void assignResponsibleIfMissing(String ticketId, String username) {
        dialogResponsibilityService.assignResponsibleIfMissing(ticketId, username);
    }

    public void markDialogAsRead(String ticketId, String operator) {
        dialogResponsibilityService.markDialogAsRead(ticketId, operator);
    }

    public void assignResponsibleIfMissingOrRedirected(String ticketId, String newResponsible, String assignedBy) {
        dialogResponsibilityService.assignResponsibleIfMissingOrRedirected(ticketId, newResponsible, assignedBy);
    }

    public List<ChatMessageDto> loadHistory(String ticketId, Long channelId) {
        return dialogConversationReadService.loadHistory(ticketId, channelId);
    }

    public Optional<DialogDetails> loadDialogDetails(String ticketId, Long channelId, String operator) {
        return dialogDetailsReadService.loadDialogDetails(ticketId, channelId, operator);
    }

    public Optional<DialogPreviousHistoryPage> loadPreviousDialogHistory(String ticketId, int offset) {
        return dialogConversationReadService.loadPreviousDialogHistory(ticketId, offset);
    }

    public List<Map<String, Object>> loadClientDialogHistory(Long userId, String currentTicketId, int limit) {
        return dialogClientContextReadService.loadClientDialogHistory(userId, currentTicketId, limit);
    }

    public Map<String, Object> loadClientProfileEnrichment(Long userId) {
        return dialogClientContextReadService.loadClientProfileEnrichment(userId);
    }

    public Map<String, Object> loadDialogProfileMatchCandidates(Map<String, String> incomingValues, int perFieldLimit) {
        return dialogClientContextReadService.loadDialogProfileMatchCandidates(incomingValues, perFieldLimit);
    }

    public List<Map<String, Object>> loadRelatedEvents(String ticketId, int limit) {
        return dialogClientContextReadService.loadRelatedEvents(ticketId, limit);
    }

    public void logDialogActionAudit(String ticketId, String actor, String action, String result, String detail) {
        dialogAuditService.logDialogActionAudit(ticketId, actor, action, result, detail);
    }

    public void logWorkspaceTelemetry(String actor,
                                      String eventType,
                                      String eventGroup,
                                      String ticketId,
                                      String reason,
                                      String errorCode,
                                      String contractVersion,
                                      Long durationMs,
                                      String experimentName,
                                      String experimentCohort,
                                      String operatorSegment,
                                      List<String> primaryKpis,
                                      List<String> secondaryKpis,
                                      String templateId,
                                      String templateName) {
        dialogAuditService.logWorkspaceTelemetry(
                actor,
                eventType,
                eventGroup,
                ticketId,
                reason,
                errorCode,
                contractVersion,
                durationMs,
                experimentName,
                experimentCohort,
                operatorSegment,
                primaryKpis,
                secondaryKpis,
                templateId,
                templateName);
    }

    public Map<String, Object> loadWorkspaceTelemetrySummary(int days, String experimentName) {
        Map<String, Object> workspaceTelemetryConfig = dialogWorkspaceTelemetryAnalyticsService.resolveWorkspaceTelemetryConfig();
        int windowDays = Math.max(1, Math.min(days, 30));
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
        Instant previousWindowEnd = windowStart;
        Instant previousWindowStart = previousWindowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
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

    public Map<String, Object> loadWorkspaceTelemetrySummary(int days,
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

        Map<String, Object> workspaceTelemetryConfig = dialogWorkspaceTelemetryAnalyticsService.resolveWorkspaceTelemetryConfig();
        List<Map<String, Object>> rows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(resolvedStart, resolvedEnd, experimentName);
        List<Map<String, Object>> previousRows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(previousWindowStart, previousWindowEnd, experimentName);
        List<Map<String, Object>> shiftRows = dialogWorkspaceTelemetryDataService.aggregateWorkspaceTelemetryRows(rows, "shift");
        List<Map<String, Object>> teamRows = dialogWorkspaceTelemetryDataService.aggregateWorkspaceTelemetryRows(rows, "team");
        Map<String, Object> totals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(rows);
        Map<String, Object> previousTotals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(previousRows);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", windowDays);
        payload.put("window_from_utc", resolvedStart.toString());
        payload.put("window_to_utc", resolvedEnd.toString());
        payload.put("generated_at", Instant.now().toString());
        payload.put("totals", totals);
        payload.put("previous_totals", previousTotals);
        payload.put("period_comparison", dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceTelemetryComparison(totals, previousTotals));
        Map<String, Object> cohortComparison = dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceCohortComparison(rows, workspaceTelemetryConfig);
        payload.put("cohort_comparison", cohortComparison);
        payload.put("rows", rows);
        payload.put("by_shift", shiftRows);
        payload.put("by_team", teamRows);
        payload.put("gap_breakdown", dialogWorkspaceTelemetryDataService.loadWorkspaceGapBreakdown(resolvedStart, resolvedEnd, experimentName));
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

    public Map<String, Object> buildMacroGovernanceAudit(Map<String, Object> settings) {
        return dialogMacroGovernanceAuditService.buildAudit(settings);
    }

    public List<String> loadTicketCategories(String ticketId) {
        return dialogConversationReadService.loadTicketCategories(ticketId);
    }

    public void setTicketCategories(String ticketId, List<String> categories) {
        dialogTicketLifecycleService.setTicketCategories(ticketId, categories);
    }

    public DialogResolveResult resolveTicket(String ticketId, String operator, List<String> categories) {
        return dialogTicketLifecycleService.resolveTicket(ticketId, operator, categories);
    }

    public DialogResolveResult reopenTicket(String ticketId, String operator) {
        return dialogTicketLifecycleService.reopenTicket(ticketId, operator);
    }
}



