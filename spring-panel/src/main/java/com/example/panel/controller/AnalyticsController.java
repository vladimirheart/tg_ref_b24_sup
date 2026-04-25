package com.example.panel.controller;

import com.example.panel.model.AnalyticsClientSummary;
import com.example.panel.model.AnalyticsTicketSummary;
import com.example.panel.service.AnalyticsService;
import com.example.panel.service.DialogAuditService;
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
    private final DialogAuditService dialogAuditService;
    private final NavigationService navigationService;
    private final SharedConfigService sharedConfigService;

    public AnalyticsController(AnalyticsService analyticsService,
                               DialogAuditService dialogAuditService,
                               NavigationService navigationService,
                               SharedConfigService sharedConfigService) {
        this.analyticsService = analyticsService;
        this.dialogAuditService = dialogAuditService;
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

    @GetMapping("/certificates")
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String sslCertificatesMonitoring(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        return "analytics/certificates";
    }

    @GetMapping("/rms-control")
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String rmsMonitoring(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        return "analytics/rms-control";
    }

    @GetMapping("/iiko-api-monitoring")
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public String iikoApiMonitoring(Model model, Authentication authentication) {
        navigationService.enrich(model, authentication);
        return "analytics/iiko-api-monitoring";
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
        String actor = authentication != null ? authentication.getName() : null;
        String reviewedBy = normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, reviewedBy);
        String reviewedAtRaw = normalize(String.valueOf(request != null ? request.reviewedAtUtc() : null));
        String reviewNote = normalize(String.valueOf(request != null ? request.reviewNote() : null));
        String decisionAction = normalize(String.valueOf(request != null ? request.decisionAction() : null));
        String incidentFollowup = normalize(String.valueOf(request != null ? request.incidentFollowup() : null));
        List<String> requiredCriteria = sanitizeStringList(request != null ? request.requiredCriteria() : null);
        List<String> checkedCriteria = sanitizeStringList(request != null ? request.checkedCriteria() : null);
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
        String previousDecisionAction = normalize(String.valueOf(
                dialogConfig.get("workspace_rollout_governance_review_decision_action")));
        if (previousDecisionAction != null) {
            previousDecisionAction = previousDecisionAction.toLowerCase(Locale.ROOT);
            if (!"go".equals(previousDecisionAction)
                    && !"hold".equals(previousDecisionAction)
                    && !"rollback".equals(previousDecisionAction)) {
                previousDecisionAction = null;
            }
        }
        String previousDecisionAt = normalize(String.valueOf(
                dialogConfig.get("workspace_rollout_governance_reviewed_at")));
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
            if (previousDecisionAction != null) {
                dialogConfig.put("workspace_rollout_governance_previous_decision_action", previousDecisionAction);
                if (previousDecisionAt == null) {
                    dialogConfig.remove("workspace_rollout_governance_previous_decision_at");
                } else {
                    dialogConfig.put("workspace_rollout_governance_previous_decision_at", previousDecisionAt);
                }
            }
            dialogConfig.put("workspace_rollout_governance_review_decision_action", decisionAction);
        }
        if (incidentFollowup == null) {
            dialogConfig.remove("workspace_rollout_governance_review_incident_followup");
        } else {
            dialogConfig.put("workspace_rollout_governance_review_incident_followup", incidentFollowup);
        }
        if (requiredCriteria.isEmpty()) {
            dialogConfig.remove("workspace_rollout_governance_review_required_criteria");
        } else {
            dialogConfig.put("workspace_rollout_governance_review_required_criteria", requiredCriteria);
        }
        if (checkedCriteria.isEmpty()) {
            dialogConfig.remove("workspace_rollout_governance_review_checked_criteria");
        } else {
            dialogConfig.put("workspace_rollout_governance_review_checked_criteria", checkedCriteria);
        }
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        Object cadenceRaw = dialogConfig.get("workspace_rollout_governance_review_cadence_days");
        long cadenceDays = parsePositiveLong(cadenceRaw);
        String dueAtUtc = cadenceDays > 0 ? reviewedAtUtc.plusDays(cadenceDays).toInstant().toString() : "";

        dialogAuditService.logWorkspaceTelemetry(
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
        if (decisionAction != null) {
            dialogAuditService.logWorkspaceTelemetry(
                    actor,
                    "workspace_rollout_review_decision_" + decisionAction,
                    "experiment",
                    null,
                    "analytics_weekly_review_decision",
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
        }
        if (incidentFollowup != null) {
            dialogAuditService.logWorkspaceTelemetry(
                    actor,
                    "workspace_rollout_review_incident_followup_linked",
                    "experiment",
                    null,
                    "analytics_weekly_review_incident_followup",
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
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "reviewed_by", reviewedBy,
                "reviewed_at_utc", reviewedAtUtc.toInstant().toString(),
                "next_review_due_at_utc", dueAtUtc,
                "review_note", reviewNote == null ? "" : reviewNote,
                "decision_action", decisionAction == null ? "" : decisionAction,
                "incident_followup", incidentFollowup == null ? "" : incidentFollowup,
                "required_criteria", requiredCriteria,
                "checked_criteria", checkedCriteria
        ));
    }

    @PostMapping(value = "/workspace-context/standard", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateWorkspaceContextStandard(@RequestBody(required = false) WorkspaceContextStandardRequest request,
                                                            Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        String reviewedBy = normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, reviewedBy);
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
        Map<String, List<String>> scenarioMandatoryFields = sanitizeStringListMap(
                request != null ? request.scenarioMandatoryFields() : null);
        List<String> sourceOfTruth = sanitizeStringList(request != null ? request.sourceOfTruth() : null);
        Map<String, List<String>> scenarioSourceOfTruth = sanitizeStringListMap(
                request != null ? request.scenarioSourceOfTruth() : null);
        List<String> priorityBlocks = sanitizeStringList(request != null ? request.priorityBlocks() : null);
        Map<String, List<String>> scenarioPriorityBlocks = sanitizeStringListMap(
                request != null ? request.scenarioPriorityBlocks() : null);
        Map<String, ContextPlaybookPayload> playbooks = sanitizeContextPlaybookMap(
                request != null ? request.playbooks() : null);
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
        if (scenarioMandatoryFields.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_mandatory_fields_by_scenario");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_mandatory_fields_by_scenario", scenarioMandatoryFields);
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
        if (scenarioSourceOfTruth.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_source_of_truth_by_scenario");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_source_of_truth_by_scenario", scenarioSourceOfTruth);
        }
        if (priorityBlocks.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_priority_blocks");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_priority_blocks", priorityBlocks);
        }
        if (scenarioPriorityBlocks.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_priority_blocks_by_scenario");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_priority_blocks_by_scenario", scenarioPriorityBlocks);
        }
        if (playbooks.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_playbooks");
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            playbooks.forEach((key, value) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("label", value.label());
                item.put("url", value.url());
                if (value.summary() != null) {
                    item.put("summary", value.summary());
                }
                payload.put(key, item);
            });
            dialogConfig.put("workspace_rollout_context_contract_playbooks", payload);
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

        dialogAuditService.logWorkspaceTelemetry(
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

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("required", required);
        response.put("reviewed_by", reviewedBy);
        response.put("reviewed_at_utc", reviewedAtUtc.toInstant().toString());
        response.put("scenarios", scenarios);
        response.put("mandatory_fields", mandatoryFields);
        response.put("scenario_mandatory_fields", scenarioMandatoryFields);
        response.put("source_of_truth", sourceOfTruth);
        response.put("scenario_source_of_truth", scenarioSourceOfTruth);
        response.put("priority_blocks", priorityBlocks);
        response.put("scenario_priority_blocks", scenarioPriorityBlocks);
        response.put("playbooks", playbooks);
        response.put("note", note == null ? "" : note);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/workspace-rollout/legacy-only-scenarios", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateWorkspaceLegacyOnlyScenarios(@RequestBody(required = false) WorkspaceLegacyOnlyScenariosRequest request,
                                                                Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        String reviewedBy = normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, reviewedBy);
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
        Map<String, LegacyScenarioMetadataPayload> scenarioMetadata;
        try {
            scenarioMetadata = sanitizeLegacyScenarioMetadataMap(
                    request != null ? request.scenarioMetadata() : null);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", ex.getMessage()));
        }
        String note = normalize(String.valueOf(request != null ? request.note() : null));
        if (note != null && note.length() > 500) {
            note = note.substring(0, 500);
        }

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        if (scenarios.isEmpty()) {
            dialogConfig.remove("workspace_rollout_governance_legacy_only_scenarios");
        } else {
            dialogConfig.put("workspace_rollout_governance_legacy_only_scenarios", scenarios);
        }
        if (scenarioMetadata.isEmpty()) {
            dialogConfig.remove("workspace_rollout_governance_legacy_only_scenario_metadata");
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            scenarioMetadata.forEach((scenario, metadata) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("owner", metadata.owner());
                if (metadata.deadlineAtUtc() != null) {
                    item.put("deadline_at_utc", metadata.deadlineAtUtc());
                }
                if (metadata.note() != null) {
                    item.put("note", metadata.note());
                }
                payload.put(scenario, item);
            });
            dialogConfig.put("workspace_rollout_governance_legacy_only_scenario_metadata", payload);
        }
        dialogConfig.put("workspace_rollout_governance_legacy_inventory_reviewed_by", reviewedBy);
        dialogConfig.put("workspace_rollout_governance_legacy_inventory_reviewed_at", reviewedAtUtc.toInstant().toString());
        if (note == null) {
            dialogConfig.remove("workspace_rollout_governance_legacy_inventory_review_note");
        } else {
            dialogConfig.put("workspace_rollout_governance_legacy_inventory_review_note", note);
        }
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        dialogAuditService.logWorkspaceTelemetry(
                actor,
                "workspace_legacy_inventory_updated",
                "experiment",
                null,
                "analytics_legacy_inventory",
                null,
                "workspace.v1",
                Long.valueOf(scenarios.size()),
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
                "scenarios", scenarios,
                "scenario_metadata", scenarioMetadata,
                "reviewed_by", reviewedBy,
                "reviewed_at_utc", reviewedAtUtc.toInstant().toString(),
                "note", note == null ? "" : note
        ));
    }

    @PostMapping(value = "/workspace-rollout/legacy-usage-policy", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateWorkspaceLegacyUsagePolicy(@RequestBody(required = false) WorkspaceLegacyUsagePolicyRequest request,
                                                          Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : null;
    String reviewedBy = normalize(String.valueOf(request != null ? request.reviewedBy() : null));
    if (reviewedBy == null) {
        reviewedBy = resolveActor(authentication, null);
    }
    actor = resolveActor(authentication, reviewedBy);
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
    String reviewNote = normalize(String.valueOf(request != null ? request.reviewNote() : null));
    if (reviewNote != null && reviewNote.length() > 500) {
        reviewNote = reviewNote.substring(0, 500);
    }
    String decision = normalize(String.valueOf(request != null ? request.decision() : null));
    if (decision != null) {
        decision = decision.toLowerCase(Locale.ROOT);
        if (!"go".equals(decision) && !"hold".equals(decision)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "decision must be one of: go, hold"));
        }
    }
    Long maxSharePct = request != null ? request.maxLegacyManualSharePct() : null;
    if (maxSharePct != null && (maxSharePct < 0L || maxSharePct > 100L)) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "max_legacy_manual_share_pct must be between 0 and 100"));
    }

    Long minWorkspaceOpenEvents = request != null ? request.minWorkspaceOpenEvents() : null;
    if (minWorkspaceOpenEvents != null && (minWorkspaceOpenEvents < 0L || minWorkspaceOpenEvents > 100000L)) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "min_workspace_open_events must be between 0 and 100000"));
    }
    Long maxShareDeltaPct = request != null ? request.maxLegacyManualShareDeltaPct() : null;
    if (maxShareDeltaPct != null && (maxShareDeltaPct < 0L || maxShareDeltaPct > 100L)) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "max_legacy_manual_share_delta_pct must be between 0 and 100"));
    }
    Long maxBlockedShareDeltaPct = request != null ? request.maxLegacyBlockedShareDeltaPct() : null;
    if (maxBlockedShareDeltaPct != null && (maxBlockedShareDeltaPct < 0L || maxBlockedShareDeltaPct > 100L)) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "max_legacy_blocked_share_delta_pct must be between 0 and 100"));
    }
    List<String> blockedReasonsReviewed = sanitizeStringList(request != null ? request.blockedReasonsReviewed() : null);
    String blockedReasonsFollowup = normalize(String.valueOf(request != null ? request.blockedReasonsFollowup() : null));
    if (blockedReasonsFollowup != null && blockedReasonsFollowup.length() > 500) {
        blockedReasonsFollowup = blockedReasonsFollowup.substring(0, 500);
    }

    Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
    Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
            ? new LinkedHashMap<>((Map<String, Object>) map)
            : new LinkedHashMap<>();
    dialogConfig.put("workspace_rollout_governance_legacy_usage_reviewed_by", reviewedBy);
    dialogConfig.put("workspace_rollout_governance_legacy_usage_reviewed_at", reviewedAtUtc.toInstant().toString());
    if (reviewNote == null) {
        dialogConfig.remove("workspace_rollout_governance_legacy_usage_review_note");
    } else {
        dialogConfig.put("workspace_rollout_governance_legacy_usage_review_note", reviewNote);
    }
    if (decision == null) {
        dialogConfig.remove("workspace_rollout_governance_legacy_usage_decision");
    } else {
        dialogConfig.put("workspace_rollout_governance_legacy_usage_decision", decision);
    }
    if (maxSharePct == null) {
        dialogConfig.remove("workspace_rollout_governance_legacy_manual_share_max_pct");
    } else {
        dialogConfig.put("workspace_rollout_governance_legacy_manual_share_max_pct", maxSharePct);
    }
    if (minWorkspaceOpenEvents == null) {
        dialogConfig.remove("workspace_rollout_governance_legacy_usage_min_workspace_open_events");
    } else {
        dialogConfig.put("workspace_rollout_governance_legacy_usage_min_workspace_open_events", minWorkspaceOpenEvents);
    }
    if (maxShareDeltaPct == null) {
        dialogConfig.remove("workspace_rollout_governance_legacy_usage_max_share_delta_pct");
    } else {
        dialogConfig.put("workspace_rollout_governance_legacy_usage_max_share_delta_pct", maxShareDeltaPct);
    }
    if (maxBlockedShareDeltaPct == null) {
        dialogConfig.remove("workspace_rollout_governance_legacy_usage_max_blocked_share_delta_pct");
    } else {
        dialogConfig.put("workspace_rollout_governance_legacy_usage_max_blocked_share_delta_pct", maxBlockedShareDeltaPct);
    }
        List<String> allowedReasons = sanitizeStringList(request != null ? request.allowedReasons() : null);
        if (allowedReasons.isEmpty()) {
            dialogConfig.remove("workspace_rollout_legacy_manual_open_allowed_reasons");
        } else {
            dialogConfig.put("workspace_rollout_legacy_manual_open_allowed_reasons", allowedReasons);
        }
        Boolean reasonCatalogRequired = request != null ? request.reasonCatalogRequired() : null;
        if (reasonCatalogRequired == null) {
            dialogConfig.remove("workspace_rollout_legacy_manual_open_reason_catalog_required");
        } else {
            dialogConfig.put("workspace_rollout_legacy_manual_open_reason_catalog_required", reasonCatalogRequired);
        }
    if (blockedReasonsReviewed.isEmpty()) {
        dialogConfig.remove("workspace_rollout_governance_legacy_blocked_reasons_reviewed");
    } else {
        dialogConfig.put("workspace_rollout_governance_legacy_blocked_reasons_reviewed", blockedReasonsReviewed);
    }
    if (blockedReasonsFollowup == null) {
        dialogConfig.remove("workspace_rollout_governance_legacy_blocked_reasons_followup");
    } else {
        dialogConfig.put("workspace_rollout_governance_legacy_blocked_reasons_followup", blockedReasonsFollowup);
    }
    settings.put("dialog_config", dialogConfig);
    sharedConfigService.saveSettings(settings);

    dialogAuditService.logWorkspaceTelemetry(
            actor,
            "workspace_legacy_usage_policy_updated",
            "experiment",
            null,
            "analytics_legacy_usage_policy",
            null,
            "workspace.v1",
            maxSharePct,
            "workspace_v1_rollout",
            null,
            null,
            null,
            null,
            null,
            null
    );

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("success", true);
    response.put("reviewed_by", reviewedBy);
    response.put("reviewed_at_utc", reviewedAtUtc.toInstant().toString());
    response.put("review_note", reviewNote == null ? "" : reviewNote);
    response.put("decision", decision == null ? "" : decision);
    response.put("max_legacy_manual_share_pct", maxSharePct == null ? "" : maxSharePct);
    response.put("min_workspace_open_events", minWorkspaceOpenEvents == null ? "" : minWorkspaceOpenEvents);
    response.put("max_legacy_manual_share_delta_pct", maxShareDeltaPct == null ? "" : maxShareDeltaPct);
    response.put("max_legacy_blocked_share_delta_pct", maxBlockedShareDeltaPct == null ? "" : maxBlockedShareDeltaPct);
    response.put("allowed_reasons", allowedReasons);
    response.put("reason_catalog_required", reasonCatalogRequired == null ? false : reasonCatalogRequired);
    response.put("blocked_reasons_reviewed", blockedReasonsReviewed);
    response.put("blocked_reasons_followup", blockedReasonsFollowup == null ? "" : blockedReasonsFollowup);
    return ResponseEntity.ok(response);
}
    @PostMapping(value = "/sla-policy/governance-review", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateSlaPolicyGovernanceReview(@RequestBody(required = false) SlaPolicyGovernanceReviewRequest request,
                                                             Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        String reviewedBy = normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, reviewedBy);
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
        String reviewNote = normalize(String.valueOf(request != null ? request.reviewNote() : null));
        if (reviewNote != null && reviewNote.length() > 500) {
            reviewNote = reviewNote.substring(0, 500);
        }
        String dryRunTicketId = normalize(String.valueOf(request != null ? request.dryRunTicketId() : null));
        if (dryRunTicketId != null && dryRunTicketId.length() > 80) {
            dryRunTicketId = dryRunTicketId.substring(0, 80);
        }
        String decision = normalize(String.valueOf(request != null ? request.decision() : null));
        if (decision != null) {
            decision = decision.toLowerCase(Locale.ROOT);
            if (!"go".equals(decision) && !"hold".equals(decision)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "decision must be one of: go, hold"));
            }
        }
        String policyChangedAtRaw = normalize(String.valueOf(request != null ? request.policyChangedAtUtc() : null));
        OffsetDateTime policyChangedAtUtc = null;
        if (policyChangedAtRaw != null) {
            policyChangedAtUtc = parseUtcTimestamp(policyChangedAtRaw);
            if (policyChangedAtUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "policy_changed_at_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        dialogConfig.put("sla_critical_auto_assign_governance_reviewed_by", reviewedBy);
        dialogConfig.put("sla_critical_auto_assign_governance_reviewed_at", reviewedAtUtc.toInstant().toString());
        if (reviewNote == null) {
            dialogConfig.remove("sla_critical_auto_assign_governance_review_note");
        } else {
            dialogConfig.put("sla_critical_auto_assign_governance_review_note", reviewNote);
        }
        if (dryRunTicketId == null) {
            dialogConfig.remove("sla_critical_auto_assign_governance_dry_run_ticket_id");
        } else {
            dialogConfig.put("sla_critical_auto_assign_governance_dry_run_ticket_id", dryRunTicketId);
        }
        if (decision == null) {
            dialogConfig.remove("sla_critical_auto_assign_governance_decision");
        } else {
            dialogConfig.put("sla_critical_auto_assign_governance_decision", decision);
        }
        if (policyChangedAtUtc != null) {
            dialogConfig.put("sla_critical_auto_assign_governance_policy_changed_at", policyChangedAtUtc.toInstant().toString());
        }
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        dialogAuditService.logWorkspaceTelemetry(
                actor,
                "workspace_sla_policy_review_updated",
                "experiment",
                null,
                "analytics_sla_policy_governance_review",
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
                "review_note", reviewNote == null ? "" : reviewNote,
                "dry_run_ticket_id", dryRunTicketId == null ? "" : dryRunTicketId,
                "decision", decision == null ? "" : decision,
                "policy_changed_at_utc", policyChangedAtUtc != null ? policyChangedAtUtc.toInstant().toString() : ""
        ));
    }

    @PostMapping(value = "/macro-governance/review", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateMacroGovernanceReview(@RequestBody(required = false) MacroGovernanceReviewRequest request,
                                                         Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        String reviewedBy = normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, reviewedBy);
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
        String reviewNote = normalize(String.valueOf(request != null ? request.reviewNote() : null));
        if (reviewNote != null && reviewNote.length() > 500) {
            reviewNote = reviewNote.substring(0, 500);
        }
        String cleanupTicketId = normalize(String.valueOf(request != null ? request.cleanupTicketId() : null));
        if (cleanupTicketId != null && cleanupTicketId.length() > 80) {
            cleanupTicketId = cleanupTicketId.substring(0, 80);
        }
        String decision = normalize(String.valueOf(request != null ? request.decision() : null));
        if (decision != null) {
            decision = decision.toLowerCase(Locale.ROOT);
            if (!"go".equals(decision) && !"hold".equals(decision)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "decision must be one of: go, hold"));
            }
        }

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        dialogConfig.put("macro_governance_reviewed_by", reviewedBy);
        dialogConfig.put("macro_governance_reviewed_at", reviewedAtUtc.toInstant().toString());
        if (reviewNote == null) {
            dialogConfig.remove("macro_governance_review_note");
        } else {
            dialogConfig.put("macro_governance_review_note", reviewNote);
        }
        if (cleanupTicketId == null) {
            dialogConfig.remove("macro_governance_cleanup_ticket_id");
        } else {
            dialogConfig.put("macro_governance_cleanup_ticket_id", cleanupTicketId);
        }
        if (decision == null) {
            dialogConfig.remove("macro_governance_review_decision");
        } else {
            dialogConfig.put("macro_governance_review_decision", decision);
        }
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        dialogAuditService.logWorkspaceTelemetry(
                actor,
                "workspace_macro_governance_review_updated",
                "experiment",
                null,
                "analytics_macro_governance_review",
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
                "review_note", reviewNote == null ? "" : reviewNote,
                "cleanup_ticket_id", cleanupTicketId == null ? "" : cleanupTicketId,
                "decision", decision == null ? "" : decision
        ));
    }

    @PostMapping(value = "/macro-governance/external-catalog-policy", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateMacroExternalCatalogPolicy(@RequestBody(required = false) MacroExternalCatalogPolicyRequest request,
                                                              Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        String verifiedBy = normalize(String.valueOf(request != null ? request.verifiedBy() : null));
        if (verifiedBy == null) {
            verifiedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, verifiedBy);
        String verifiedAtRaw = normalize(String.valueOf(request != null ? request.verifiedAtUtc() : null));
        OffsetDateTime verifiedAtUtc;
        if (verifiedAtRaw == null) {
            verifiedAtUtc = OffsetDateTime.now(ZoneOffset.UTC);
        } else {
            verifiedAtUtc = parseUtcTimestamp(verifiedAtRaw);
            if (verifiedAtUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "verified_at_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }
        String expectedVersion = normalize(String.valueOf(request != null ? request.expectedVersion() : null));
        if (expectedVersion != null && expectedVersion.length() > 120) {
            expectedVersion = expectedVersion.substring(0, 120);
        }
        String observedVersion = normalize(String.valueOf(request != null ? request.observedVersion() : null));
        if (observedVersion != null && observedVersion.length() > 120) {
            observedVersion = observedVersion.substring(0, 120);
        }
        String reviewNote = normalize(String.valueOf(request != null ? request.reviewNote() : null));
        if (reviewNote != null && reviewNote.length() > 500) {
            reviewNote = reviewNote.substring(0, 500);
        }
        String decision = normalize(String.valueOf(request != null ? request.decision() : null));
        if (decision != null) {
            decision = decision.toLowerCase(Locale.ROOT);
            if (!"go".equals(decision) && !"hold".equals(decision)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "decision must be one of: go, hold"));
            }
        }
        long reviewTtlHours = parsePositiveLong(request != null ? request.reviewTtlHours() : null);
        if (reviewTtlHours <= 0) {
            reviewTtlHours = 168;
        }
        reviewTtlHours = Math.min(reviewTtlHours, 24L * 90L);

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        dialogConfig.put("macro_external_catalog_verified_by", verifiedBy);
        dialogConfig.put("macro_external_catalog_verified_at", verifiedAtUtc.toInstant().toString());
        dialogConfig.put("macro_external_catalog_contract_ttl_hours", reviewTtlHours);
        if (expectedVersion == null) {
            dialogConfig.remove("macro_external_catalog_expected_version");
        } else {
            dialogConfig.put("macro_external_catalog_expected_version", expectedVersion);
        }
        if (observedVersion == null) {
            dialogConfig.remove("macro_external_catalog_observed_version");
        } else {
            dialogConfig.put("macro_external_catalog_observed_version", observedVersion);
        }
        if (reviewNote == null) {
            dialogConfig.remove("macro_external_catalog_review_note");
        } else {
            dialogConfig.put("macro_external_catalog_review_note", reviewNote);
        }
        if (decision == null) {
            dialogConfig.remove("macro_external_catalog_decision");
        } else {
            dialogConfig.put("macro_external_catalog_decision", decision);
        }
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        dialogAuditService.logWorkspaceTelemetry(
                actor,
                "workspace_macro_external_catalog_policy_updated",
                "experiment",
                null,
                "analytics_macro_external_catalog_policy",
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
                "verified_by", verifiedBy,
                "verified_at_utc", verifiedAtUtc.toInstant().toString(),
                "expected_version", expectedVersion == null ? "" : expectedVersion,
                "observed_version", observedVersion == null ? "" : observedVersion,
                "review_note", reviewNote == null ? "" : reviewNote,
                "decision", decision == null ? "" : decision,
                "review_ttl_hours", reviewTtlHours
        ));
    }

    @PostMapping(value = "/macro-governance/deprecation-policy", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateMacroDeprecationPolicy(@RequestBody(required = false) MacroDeprecationPolicyRequest request,
                                                          Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        String reviewedBy = normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = resolveActor(authentication, null);
        }
        actor = resolveActor(authentication, reviewedBy);
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
        String deprecationTicketId = normalize(String.valueOf(request != null ? request.deprecationTicketId() : null));
        if (deprecationTicketId != null && deprecationTicketId.length() > 80) {
            deprecationTicketId = deprecationTicketId.substring(0, 80);
        }
        String reviewNote = normalize(String.valueOf(request != null ? request.reviewNote() : null));
        if (reviewNote != null && reviewNote.length() > 500) {
            reviewNote = reviewNote.substring(0, 500);
        }
        String decision = normalize(String.valueOf(request != null ? request.decision() : null));
        if (decision != null) {
            decision = decision.toLowerCase(Locale.ROOT);
            if (!"go".equals(decision) && !"hold".equals(decision)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "decision must be one of: go, hold"));
            }
        }
        long reviewTtlHours = parsePositiveLong(request != null ? request.reviewTtlHours() : null);
        if (reviewTtlHours <= 0) {
            reviewTtlHours = 168;
        }
        reviewTtlHours = Math.min(reviewTtlHours, 24L * 90L);

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        dialogConfig.put("macro_deprecation_policy_reviewed_by", reviewedBy);
        dialogConfig.put("macro_deprecation_policy_reviewed_at", reviewedAtUtc.toInstant().toString());
        dialogConfig.put("macro_deprecation_policy_ttl_hours", reviewTtlHours);
        if (deprecationTicketId == null) {
            dialogConfig.remove("macro_deprecation_policy_ticket_id");
        } else {
            dialogConfig.put("macro_deprecation_policy_ticket_id", deprecationTicketId);
        }
        if (reviewNote == null) {
            dialogConfig.remove("macro_deprecation_policy_review_note");
        } else {
            dialogConfig.put("macro_deprecation_policy_review_note", reviewNote);
        }
        if (decision == null) {
            dialogConfig.remove("macro_deprecation_policy_decision");
        } else {
            dialogConfig.put("macro_deprecation_policy_decision", decision);
        }
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        dialogAuditService.logWorkspaceTelemetry(
                actor,
                "workspace_macro_deprecation_policy_updated",
                "experiment",
                null,
                "analytics_macro_deprecation_policy",
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
                "deprecation_ticket_id", deprecationTicketId == null ? "" : deprecationTicketId,
                "review_note", reviewNote == null ? "" : reviewNote,
                "decision", decision == null ? "" : decision,
                "review_ttl_hours", reviewTtlHours
        ));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private static String resolveActor(Authentication authentication, String fallback) {
        if (authentication != null && normalize(authentication.getName()) != null) {
            return authentication.getName();
        }
        if (normalize(fallback) != null) {
            return fallback;
        }
        return "anonymous";
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
                                                 String incidentFollowup,
                                                 Object requiredCriteria,
                                                 Object checkedCriteria) {
    }

    private record WorkspaceContextStandardRequest(Boolean required,
                                                   Object scenarios,
                                                   Object mandatoryFields,
                                                  Object scenarioMandatoryFields,
                                                   Object sourceOfTruth,
                                                  Object scenarioSourceOfTruth,
                                                   Object priorityBlocks,
                                                  Object scenarioPriorityBlocks,
                                                   Object playbooks,
                                                   String reviewedBy,
                                                   String reviewedAtUtc,
                                                   String note) {
    }

    private static Map<String, List<String>> sanitizeStringListMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalize(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (key == null || key.length() > 120) {
                continue;
            }
            List<String> values = sanitizeStringList(entry.getValue());
            if (!values.isEmpty()) {
                result.put(key.toLowerCase(Locale.ROOT), values);
            }
        }
        return result;
    }

    private static Map<String, ContextPlaybookPayload> sanitizeContextPlaybookMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, ContextPlaybookPayload> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalize(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (key == null || key.length() > 160) {
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            Object labelRaw = item.get("label");
            Object urlRaw = item.get("url");
            Object summaryRaw = item.get("summary");
            String label = normalize(labelRaw == null ? null : String.valueOf(labelRaw));
            String url = normalize(urlRaw == null ? null : String.valueOf(urlRaw));
            String summary = normalize(summaryRaw == null ? null : String.valueOf(summaryRaw));
            if (label != null && label.length() > 160) {
                label = label.substring(0, 160);
            }
            if (summary != null && summary.length() > 300) {
                summary = summary.substring(0, 300);
            }
            if (url == null || (!url.startsWith("https://") && !url.startsWith("http://"))) {
                continue;
            }
            result.put(key.toLowerCase(Locale.ROOT), new ContextPlaybookPayload(
                    label == null ? "Playbook" : label,
                    url,
                    summary));
        }
        return result;
    }

    private record WorkspaceLegacyOnlyScenariosRequest(Object scenarios,
                                                       Object scenarioMetadata,
                                                       String reviewedBy,
                                                       String reviewedAtUtc,
                                                       String note) {
    }

    private static Map<String, LegacyScenarioMetadataPayload> sanitizeLegacyScenarioMetadataMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, LegacyScenarioMetadataPayload> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String scenario = normalize(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (scenario == null || scenario.length() > 120 || !(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            Object ownerRaw = item.get("owner");
            String owner = normalize(ownerRaw == null ? null : String.valueOf(ownerRaw));
            if (owner != null && owner.length() > 120) {
                owner = owner.substring(0, 120);
            }
            Object deadlineAtRaw = item.get("deadlineAtUtc");
            String deadlineRaw = normalize(deadlineAtRaw == null ? null : String.valueOf(deadlineAtRaw));
            if (deadlineRaw == null) {
                Object deadlineSnakeRaw = item.get("deadline_at_utc");
                deadlineRaw = normalize(deadlineSnakeRaw == null ? null : String.valueOf(deadlineSnakeRaw));
            }
            String deadlineAtUtc = null;
            if (deadlineRaw != null) {
                OffsetDateTime parsed = parseUtcTimestamp(deadlineRaw);
                if (parsed == null) {
                    throw new IllegalArgumentException("legacy scenario deadline_at_utc must be a valid UTC timestamp (ISO-8601)");
                }
                deadlineAtUtc = parsed.toInstant().toString();
            }
            Object noteRaw = item.get("note");
            String note = normalize(noteRaw == null ? null : String.valueOf(noteRaw));
            if (note != null && note.length() > 300) {
                note = note.substring(0, 300);
            }
            if (owner != null || deadlineAtUtc != null || note != null) {
                result.put(scenario.toLowerCase(Locale.ROOT), new LegacyScenarioMetadataPayload(owner, deadlineAtUtc, note));
            }
        }
        return result;
    }

    private record ContextPlaybookPayload(String label,
                                          String url,
                                          String summary) {
    }

    private record LegacyScenarioMetadataPayload(String owner,
                                                 String deadlineAtUtc,
                                                 String note) {
    }

    private record WorkspaceLegacyUsagePolicyRequest(String reviewedBy,
                                                 String reviewedAtUtc,
                                                 String reviewNote,
                                                 String decision,
                                                 Long maxLegacyManualSharePct,
                                                 Long minWorkspaceOpenEvents,
                                                 Long maxLegacyManualShareDeltaPct,
                                                 Long maxLegacyBlockedShareDeltaPct,
                                                 Object allowedReasons,
                                                 Boolean reasonCatalogRequired,
                                                 Object blockedReasonsReviewed,
                                                 String blockedReasonsFollowup) {
    }
    private record SlaPolicyGovernanceReviewRequest(String reviewedBy,
                                                    String reviewedAtUtc,
                                                    String reviewNote,
                                                    String dryRunTicketId,
                                                    String decision,
                                                    String policyChangedAtUtc) {
    }

    private record MacroGovernanceReviewRequest(String reviewedBy,
                                                String reviewedAtUtc,
                                                String reviewNote,
                                                String cleanupTicketId,
                                                String decision) {
    }

    private record MacroExternalCatalogPolicyRequest(String verifiedBy,
                                                     String verifiedAtUtc,
                                                     String expectedVersion,
                                                     String observedVersion,
                                                     String reviewNote,
                                                     String decision,
                                                     Long reviewTtlHours) {
    }

    private record MacroDeprecationPolicyRequest(String reviewedBy,
                                                 String reviewedAtUtc,
                                                 String deprecationTicketId,
                                                 String reviewNote,
                                                 String decision,
                                                 Long reviewTtlHours) {
    }
}
