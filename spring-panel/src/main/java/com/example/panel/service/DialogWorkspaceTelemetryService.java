package com.example.panel.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogWorkspaceTelemetryService {

    private static final Map<String, String> WORKSPACE_TELEMETRY_EVENT_GROUPS = Map.ofEntries(
            Map.entry("workspace_open_ms", "performance"),
            Map.entry("workspace_render_error", "stability"),
            Map.entry("workspace_fallback_to_legacy", "stability"),
            Map.entry("workspace_guardrail_breach", "stability"),
            Map.entry("workspace_abandon", "engagement"),
            Map.entry("workspace_experiment_exposure", "experiment"),
            Map.entry("workspace_draft_saved", "workspace"),
            Map.entry("workspace_draft_restored", "workspace"),
            Map.entry("workspace_reply_target_selected", "workspace"),
            Map.entry("workspace_reply_target_cleared", "workspace"),
            Map.entry("workspace_media_sent", "workspace"),
            Map.entry("workspace_context_profile_gap", "workspace"),
            Map.entry("workspace_context_source_gap", "workspace"),
            Map.entry("workspace_context_attribute_policy_gap", "workspace"),
            Map.entry("workspace_context_block_gap", "workspace"),
            Map.entry("workspace_context_contract_gap", "workspace"),
            Map.entry("workspace_context_sources_expanded", "workspace"),
            Map.entry("workspace_context_attribute_policy_expanded", "workspace"),
            Map.entry("workspace_context_extra_attributes_expanded", "workspace"),
            Map.entry("workspace_sla_policy_gap", "workspace"),
            Map.entry("workspace_parity_gap", "workspace"),
            Map.entry("workspace_inline_navigation", "workspace"),
            Map.entry("workspace_open_legacy_manual", "workspace"),
            Map.entry("workspace_open_legacy_blocked", "workspace"),
            Map.entry("workspace_rollout_packet_viewed", "experiment"),
            Map.entry("kpi_frt_recorded", "kpi"),
            Map.entry("kpi_ttr_recorded", "kpi"),
            Map.entry("kpi_sla_breach_recorded", "kpi"),
            Map.entry("kpi_dialogs_per_shift_recorded", "kpi"),
            Map.entry("kpi_csat_recorded", "kpi"),
            Map.entry("macro_preview", "macro"),
            Map.entry("macro_apply", "macro"),
            Map.entry("triage_view_switch", "triage"),
            Map.entry("triage_preferences_saved", "triage"),
            Map.entry("triage_quick_assign", "triage"),
            Map.entry("triage_quick_snooze", "triage"),
            Map.entry("triage_quick_close", "triage"),
            Map.entry("triage_bulk_action", "triage")
    );

    private final DialogWorkspaceTelemetrySummaryService dialogWorkspaceTelemetrySummaryService;
    private final DialogAuditService dialogAuditService;
    private final DialogLookupReadService dialogLookupReadService;
    private final DialogMacroGovernanceAuditService dialogMacroGovernanceAuditService;
    private final SharedConfigService sharedConfigService;
    private final SlaEscalationWebhookNotifier slaEscalationWebhookNotifier;
    private final DialogWorkspaceTelemetryControlService dialogWorkspaceTelemetryControlService;

    public DialogWorkspaceTelemetryService(DialogWorkspaceTelemetrySummaryService dialogWorkspaceTelemetrySummaryService,
                                           DialogAuditService dialogAuditService,
                                           DialogLookupReadService dialogLookupReadService,
                                           DialogMacroGovernanceAuditService dialogMacroGovernanceAuditService,
                                           SharedConfigService sharedConfigService,
                                           SlaEscalationWebhookNotifier slaEscalationWebhookNotifier,
                                           DialogWorkspaceTelemetryControlService dialogWorkspaceTelemetryControlService) {
        this.dialogWorkspaceTelemetrySummaryService = dialogWorkspaceTelemetrySummaryService;
        this.dialogAuditService = dialogAuditService;
        this.dialogLookupReadService = dialogLookupReadService;
        this.dialogMacroGovernanceAuditService = dialogMacroGovernanceAuditService;
        this.sharedConfigService = sharedConfigService;
        this.slaEscalationWebhookNotifier = slaEscalationWebhookNotifier;
        this.dialogWorkspaceTelemetryControlService = dialogWorkspaceTelemetryControlService;
    }

    public ResponseEntity<?> logTelemetry(String operator, WorkspaceTelemetryRequest request) {
        if (request == null || request.eventType() == null || request.eventType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "event_type is required"));
        }
        String normalizedEventType = request.eventType().trim().toLowerCase();
        String eventGroup = WORKSPACE_TELEMETRY_EVENT_GROUPS.get(normalizedEventType);
        if (eventGroup == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "unsupported event_type",
                    "allowed_event_types", WORKSPACE_TELEMETRY_EVENT_GROUPS.keySet()));
        }
        dialogAuditService.logWorkspaceTelemetry(
                operator,
                normalizedEventType,
                eventGroup,
                request.ticketId(),
                request.reason(),
                request.errorCode(),
                request.contractVersion(),
                request.durationMs(),
                request.experimentName(),
                request.experimentCohort(),
                request.operatorSegment(),
                request.primaryKpis(),
                request.secondaryKpis(),
                request.templateId(),
                request.templateName());
        maybeAuditMacroUsage(operator, request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    public ResponseEntity<?> loadSummary(Integer days,
                                         String experimentName,
                                         String fromUtcRaw,
                                         String toUtcRaw) {
        int safeDays = days != null ? days : 7;
        String fromUtcValue = trimToNull(fromUtcRaw);
        String toUtcValue = trimToNull(toUtcRaw);
        boolean explicitWindowRequested = fromUtcValue != null || toUtcValue != null;
        OffsetDateTime fromUtc = null;
        OffsetDateTime toUtc = null;
        if (fromUtcValue != null) {
            fromUtc = parseUtcTimestamp(fromUtcValue);
            if (fromUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "from_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }
        if (toUtcValue != null) {
            toUtc = parseUtcTimestamp(toUtcValue);
            if (toUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "to_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }
        if (fromUtc != null && toUtc != null && !fromUtc.isBefore(toUtc)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "from_utc must be earlier than to_utc"));
        }

        Map<String, Object> payload = explicitWindowRequested
                ? new LinkedHashMap<>(dialogWorkspaceTelemetrySummaryService.loadSummary(
                safeDays,
                experimentName,
                fromUtc != null ? fromUtc.toInstant() : null,
                toUtc != null ? toUtc.toInstant() : null))
                : new LinkedHashMap<>(dialogWorkspaceTelemetrySummaryService.loadSummary(safeDays, experimentName));
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Map<String, Object> slaPolicyAudit = slaEscalationWebhookNotifier.buildRoutingGovernanceAudit(
                dialogLookupReadService.loadDialogs(null),
                settings);
        Map<String, Object> macroGovernanceAudit = dialogMacroGovernanceAuditService.buildAudit(settings);
        payload.put("sla_policy_audit", slaPolicyAudit != null ? slaPolicyAudit : Map.of());
        payload.put("macro_governance_audit", macroGovernanceAudit);
        payload.put("p1_operational_control", dialogWorkspaceTelemetryControlService.buildP1OperationalControl(payload));
        payload.put("sla_review_path_control", dialogWorkspaceTelemetryControlService.buildSlaReviewPathControl(payload, slaPolicyAudit));
        payload.put("p2_governance_control", dialogWorkspaceTelemetryControlService.buildP2GovernanceControl(payload, slaPolicyAudit, macroGovernanceAudit));
        payload.put("weekly_review_focus", dialogWorkspaceTelemetryControlService.buildWorkspaceWeeklyReviewFocus(payload, slaPolicyAudit, macroGovernanceAudit));
        payload.put("success", true);
        return ResponseEntity.ok(payload);
    }

    private void maybeAuditMacroUsage(String operator, WorkspaceTelemetryRequest request) {
        if (request == null || !"macro_apply".equalsIgnoreCase(String.valueOf(request.eventType())) || !StringUtils.hasText(request.ticketId())) {
            return;
        }
        StringBuilder detail = new StringBuilder("Macro applied from workspace telemetry");
        if (StringUtils.hasText(request.templateName())) {
            detail.append(": ").append(request.templateName().trim());
        }
        if (StringUtils.hasText(request.templateId())) {
            detail.append(" [").append(request.templateId().trim()).append("]");
        }
        dialogAuditService.logDialogActionAudit(request.ticketId(), operator, "macro_apply", "success", detail.toString());
    }

    private OffsetDateTime parseUtcTimestamp(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(rawValue).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record WorkspaceTelemetryRequest(String eventType,
                                            String timestamp,
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
    }
}
