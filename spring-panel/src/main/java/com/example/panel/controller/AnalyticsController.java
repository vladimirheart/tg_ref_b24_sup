package com.example.panel.controller;

import com.example.panel.model.AnalyticsClientSummary;
import com.example.panel.model.AnalyticsTicketSummary;
import com.example.panel.service.AnalyticsService;
import com.example.panel.service.DialogService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.SharedConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.PrintWriter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Controller
@RequestMapping("/analytics")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final DialogService dialogService;
    private final NavigationService navigationService;
    private final SharedConfigService sharedConfigService;

    public AnalyticsController(AnalyticsService analyticsService,
                               DialogService dialogService,
                               NavigationService navigationService,
                               SharedConfigService sharedConfigService) {
        this.analyticsService = analyticsService;
        this.dialogService = dialogService;
        this.navigationService = navigationService;
        this.sharedConfigService = sharedConfigService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String view(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        try {
            List<AnalyticsTicketSummary> ticketSummary = analyticsService.loadTicketSummary();
            List<AnalyticsClientSummary> clientSummary = analyticsService.loadClientSummary();
            model.addAttribute("ticketSummary", ticketSummary);
            model.addAttribute("clientSummary", clientSummary);
            Map<String, Object> settings = sharedConfigService.loadSettings();
            Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            model.addAttribute("crossProductOmnichannelDashboardUrl", String.valueOf(dialogConfig.getOrDefault("cross_product_omnichannel_dashboard_url", "")).trim());
            model.addAttribute("crossProductOmnichannelDashboardLabel", String.valueOf(dialogConfig.getOrDefault("cross_product_omnichannel_dashboard_label", "Omni-channel KPI dashboard")).trim());
            model.addAttribute("crossProductFinanceDashboardUrl", String.valueOf(dialogConfig.getOrDefault("cross_product_finance_dashboard_url", "")).trim());
            model.addAttribute("crossProductFinanceDashboardLabel", String.valueOf(dialogConfig.getOrDefault("cross_product_finance_dashboard_label", "Финансовый KPI dashboard")).trim());
            model.addAttribute("crossProductDashboardLinksRequired", Boolean.parseBoolean(
                    String.valueOf(dialogConfig.getOrDefault("workspace_rollout_external_kpi_dashboard_links_required", false))));
            model.addAttribute("crossProductOmnichannelReady", Boolean.parseBoolean(
                    String.valueOf(dialogConfig.getOrDefault("workspace_rollout_external_kpi_omnichannel_ready", false))));
            model.addAttribute("crossProductFinanceReady", Boolean.parseBoolean(
                    String.valueOf(dialogConfig.getOrDefault("workspace_rollout_external_kpi_finance_ready", false))));
            String dependencyTicketUrl = String.valueOf(dialogConfig.getOrDefault(
                    "workspace_rollout_external_kpi_datamart_dependency_ticket_url", "")).trim();
            model.addAttribute("crossProductDependencyTicketUrl", dependencyTicketUrl);
            model.addAttribute("crossProductDependencyTicketRequired", Boolean.parseBoolean(
                    String.valueOf(dialogConfig.getOrDefault(
                            "workspace_rollout_external_kpi_datamart_dependency_ticket_required", false))));
            model.addAttribute("crossProductDependencyTicketFreshnessRequired", Boolean.parseBoolean(
                    String.valueOf(dialogConfig.getOrDefault(
                            "workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required", false))));
            model.addAttribute("crossProductDependencyTicketUpdatedAt", String.valueOf(dialogConfig.getOrDefault(
                    "workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at", "")).trim());
            model.addAttribute("crossProductDependencyTicketTtlHours", String.valueOf(dialogConfig.getOrDefault(
                    "workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours", "336")).trim());

            log.info("Analytics view requested by {}: {} ticket rows, {} client rows",
                    authentication.getName(), ticketSummary.size(), clientSummary.size());
        } catch (Exception ex) {
            log.error("Failed to load analytics page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "analytics/index";
    }

    @PostMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<StreamingResponseBody> export(@RequestBody(required = false) Map<String, Object> request) {
        StreamingResponseBody body = outputStream -> {
            try (PrintWriter writer = new PrintWriter(outputStream)) {
                writer.println("business,city,status,total");
                analyticsService.loadTicketSummary().forEach(row -> writer.printf("%s,%s,%s,%d%n",
                        row.business(), row.city(), row.status(), row.total()));
            }
        };
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=analytics.csv")
                .body(body);
    }

    @PostMapping(value = "/workspace-rollout/review", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> confirmWorkspaceRolloutReview(@RequestBody(required = false) WorkspaceRolloutReviewRequest request,
                                                           Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "anonymous";
        String reviewedBy = normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = actor;
        }
        String reviewedAtRaw = normalize(String.valueOf(request != null ? request.reviewedAtUtc() : null));
        String reviewNote = normalize(String.valueOf(request != null ? request.reviewNote() : null));
        String decisionAction = normalize(String.valueOf(request != null ? request.decisionAction() : null));
        String incidentFollowup = normalize(String.valueOf(request != null ? request.incidentFollowup() : null));
        if (reviewNote != null && reviewNote.length() > 500) {
            reviewNote = reviewNote.substring(0, 500);
        }
        if (incidentFollowup != null && incidentFollowup.length() > 255) {
            incidentFollowup = incidentFollowup.substring(0, 255);
        }
        if (decisionAction != null) {
            decisionAction = decisionAction.toLowerCase(Locale.ROOT);
            if (!"go".equals(decisionAction) && !"hold".equals(decisionAction) && !"rollback".equals(decisionAction)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "decision_action must be one of: go, hold, rollback"));
            }
        }
        OffsetDateTime reviewedAtUtc;
        if (reviewedAtRaw == null) {
            reviewedAtUtc = OffsetDateTime.now(ZoneOffset.UTC);
        } else {
            reviewedAtUtc = parseUtcTimestamp(reviewedAtRaw);
            if (reviewedAtUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        dialogConfig.put("workspace_rollout_governance_reviewed_by", reviewedBy);
        dialogConfig.put("workspace_rollout_governance_reviewed_at", reviewedAtUtc.toInstant().toString());
        if (reviewNote == null) {
            dialogConfig.remove("workspace_rollout_governance_review_note");
        } else {
            dialogConfig.put("workspace_rollout_governance_review_note", reviewNote);
        }
        if (decisionAction == null) {
            dialogConfig.remove("workspace_rollout_governance_review_decision_action");
        } else {
            dialogConfig.put("workspace_rollout_governance_review_decision_action", decisionAction);
        }
        if (incidentFollowup == null) {
            dialogConfig.remove("workspace_rollout_governance_review_incident_followup");
        } else {
            dialogConfig.put("workspace_rollout_governance_review_incident_followup", incidentFollowup);
        }
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        Object cadenceRaw = dialogConfig.get("workspace_rollout_governance_review_cadence_days");
        long cadenceDays = parsePositiveLong(cadenceRaw);
        String dueAtUtc = cadenceDays > 0 ? reviewedAtUtc.plusDays(cadenceDays).toInstant().toString() : "";

        dialogService.logWorkspaceTelemetry(
                actor,
                "workspace_rollout_review_confirmed",
                "experiment",
                null,
                "analytics_weekly_review",
                null,
                "workspace.v1",
                null,
                "workspace_v1_rollout",
                null,
                null,
                null,
                null,
                null,
                null
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "reviewed_by", reviewedBy,
                "reviewed_at_utc", reviewedAtUtc.toInstant().toString(),
                "next_review_due_at_utc", dueAtUtc,
                "review_note", reviewNote == null ? "" : reviewNote,
                "decision_action", decisionAction == null ? "" : decisionAction,
                "incident_followup", incidentFollowup == null ? "" : incidentFollowup
        ));
    }

    @PostMapping(value = "/workspace-context/standard", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateWorkspaceContextStandard(@RequestBody(required = false) WorkspaceContextStandardRequest request,
                                                            Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : "anonymous";
        String reviewedBy = normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = actor;
        }
        String reviewedAtRaw = normalize(String.valueOf(request != null ? request.reviewedAtUtc() : null));
        OffsetDateTime reviewedAtUtc;
        if (reviewedAtRaw == null) {
            reviewedAtUtc = OffsetDateTime.now(ZoneOffset.UTC);
        } else {
            reviewedAtUtc = parseUtcTimestamp(reviewedAtRaw);
            if (reviewedAtUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }

        List<String> scenarios = sanitizeStringList(request != null ? request.scenarios() : null);
        List<String> mandatoryFields = sanitizeStringList(request != null ? request.mandatoryFields() : null);
        List<String> sourceOfTruth = sanitizeStringList(request != null ? request.sourceOfTruth() : null);
        List<String> priorityBlocks = sanitizeStringList(request != null ? request.priorityBlocks() : null);
        String note = normalize(String.valueOf(request != null ? request.note() : null));
        if (note != null && note.length() > 500) {
            note = note.substring(0, 500);
        }
        boolean required = request != null && Boolean.TRUE.equals(request.required());

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        dialogConfig.put("workspace_rollout_context_contract_required", required);
        if (scenarios.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_scenarios");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_scenarios", scenarios);
        }
        if (mandatoryFields.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_mandatory_fields");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_mandatory_fields", mandatoryFields);
        }
        if (sourceOfTruth.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_source_of_truth");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_source_of_truth", sourceOfTruth);
        }
        if (priorityBlocks.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_priority_blocks");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_priority_blocks", priorityBlocks);
        }
        dialogConfig.put("workspace_rollout_context_contract_reviewed_by", reviewedBy);
        dialogConfig.put("workspace_rollout_context_contract_reviewed_at", reviewedAtUtc.toInstant().toString());
        if (note == null) {
            dialogConfig.remove("workspace_rollout_context_contract_review_note");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_review_note", note);
        }
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        dialogService.logWorkspaceTelemetry(
                actor,
                "workspace_context_contract_updated",
                "experiment",
                null,
                "analytics_context_standard",
                null,
                "workspace.v1",
                null,
                "workspace_v1_rollout",
                null,
                null,
                null,
                null,
                null,
                null
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "required", required,
                "reviewed_by", reviewedBy,
                "reviewed_at_utc", reviewedAtUtc.toInstant().toString(),
                "scenarios", scenarios,
                "mandatory_fields", mandatoryFields,
                "source_of_truth", sourceOfTruth,
                "priority_blocks", priorityBlocks,
                "note", note == null ? "" : note
        ));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static OffsetDateTime parseUtcTimestamp(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(value);
            if (!ZoneOffset.UTC.equals(parsed.getOffset())) {
                return null;
            }
            return parsed.withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                Instant parsed = Instant.parse(value);
                return value.endsWith("Z") ? parsed.atOffset(ZoneOffset.UTC) : null;
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static long parsePositiveLong(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(String.valueOf(value).trim()));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static List<String> sanitizeStringList(Object raw) {
        Stream<?> stream;
        if (raw instanceof List<?> list) {
            stream = list.stream();
        } else if (raw instanceof String value) {
            stream = Stream.of(value.split("[,\\n;]"));
        } else {
            return List.of();
        }
        return stream
                .map(item -> normalize(item == null ? null : String.valueOf(item)))
                .filter(value -> value != null && value.length() <= 120)
                .distinct()
                .toList();
    }

    private record WorkspaceRolloutReviewRequest(String reviewedBy,
                                                 String reviewedAtUtc,
                                                 String reviewNote,
                                                 String decisionAction,
                                                 String incidentFollowup) {
    }

    private record WorkspaceContextStandardRequest(Boolean required,
                                                   Object scenarios,
                                                   Object mandatoryFields,
                                                   Object sourceOfTruth,
                                                   Object priorityBlocks,
                                                   String reviewedBy,
                                                   String reviewedAtUtc,
                                                   String note) {
    }
}
