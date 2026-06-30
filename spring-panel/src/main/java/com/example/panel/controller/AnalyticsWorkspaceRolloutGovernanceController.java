package com.example.panel.controller;

import com.example.panel.service.DialogAuditService;
import com.example.panel.service.SharedConfigService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsWorkspaceRolloutGovernanceController {

    private final DialogAuditService dialogAuditService;
    private final SharedConfigService sharedConfigService;

    public AnalyticsWorkspaceRolloutGovernanceController(DialogAuditService dialogAuditService,
                                                         SharedConfigService sharedConfigService) {
        this.dialogAuditService = dialogAuditService;
        this.sharedConfigService = sharedConfigService;
    }

    @PostMapping(value = "/workspace-rollout/review", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> confirmWorkspaceRolloutReview(
            @RequestBody(required = false) AnalyticsControllerSupport.WorkspaceRolloutReviewRequest request,
            Authentication authentication) {
        String reviewedBy = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = AnalyticsControllerSupport.resolveActor(authentication, null);
        }
        String actor = AnalyticsControllerSupport.resolveActor(authentication, reviewedBy);
        String reviewedAtRaw = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewedAtUtc() : null));
        String reviewNote = AnalyticsControllerSupport.truncate(
                AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewNote() : null)),
                500);
        String decisionAction = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.decisionAction() : null));
        String incidentFollowup = AnalyticsControllerSupport.truncate(
                AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.incidentFollowup() : null)),
                255);
        List<String> requiredCriteria = AnalyticsControllerSupport.sanitizeStringList(
                request != null ? request.requiredCriteria() : null);
        List<String> checkedCriteria = AnalyticsControllerSupport.sanitizeStringList(
                request != null ? request.checkedCriteria() : null);
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
            reviewedAtUtc = AnalyticsControllerSupport.parseUtcTimestamp(reviewedAtRaw);
            if (reviewedAtUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = AnalyticsControllerSupport.mutableDialogConfig(settings);
        String previousDecisionAction = AnalyticsControllerSupport.normalize(String.valueOf(
                dialogConfig.get("workspace_rollout_governance_review_decision_action")));
        if (previousDecisionAction != null) {
            previousDecisionAction = previousDecisionAction.toLowerCase(Locale.ROOT);
            if (!"go".equals(previousDecisionAction)
                    && !"hold".equals(previousDecisionAction)
                    && !"rollback".equals(previousDecisionAction)) {
                previousDecisionAction = null;
            }
        }
        String previousDecisionAt = AnalyticsControllerSupport.normalize(String.valueOf(
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

        long cadenceDays = AnalyticsControllerSupport.parsePositiveLong(
                dialogConfig.get("workspace_rollout_governance_review_cadence_days"));
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

    @PostMapping(value = "/workspace-rollout/legacy-only-scenarios", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateWorkspaceLegacyOnlyScenarios(
            @RequestBody(required = false) AnalyticsControllerSupport.WorkspaceLegacyOnlyScenariosRequest request,
            Authentication authentication) {
        String reviewedBy = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = AnalyticsControllerSupport.resolveActor(authentication, null);
        }
        String actor = AnalyticsControllerSupport.resolveActor(authentication, reviewedBy);
        String reviewedAtRaw = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewedAtUtc() : null));

        OffsetDateTime reviewedAtUtc;
        if (reviewedAtRaw == null) {
            reviewedAtUtc = OffsetDateTime.now(ZoneOffset.UTC);
        } else {
            reviewedAtUtc = AnalyticsControllerSupport.parseUtcTimestamp(reviewedAtRaw);
            if (reviewedAtUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }

        List<String> scenarios = AnalyticsControllerSupport.sanitizeStringList(request != null ? request.scenarios() : null);
        Map<String, AnalyticsControllerSupport.LegacyScenarioMetadataPayload> scenarioMetadata;
        try {
            scenarioMetadata = AnalyticsControllerSupport.sanitizeLegacyScenarioMetadataMap(
                    request != null ? request.scenarioMetadata() : null);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", ex.getMessage()));
        }
        String note = AnalyticsControllerSupport.truncate(
                AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.note() : null)),
                500);

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = AnalyticsControllerSupport.mutableDialogConfig(settings);
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
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateWorkspaceLegacyUsagePolicy(
            @RequestBody(required = false) AnalyticsControllerSupport.WorkspaceLegacyUsagePolicyRequest request,
            Authentication authentication) {
        String reviewedBy = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = AnalyticsControllerSupport.resolveActor(authentication, null);
        }
        String actor = AnalyticsControllerSupport.resolveActor(authentication, reviewedBy);
        String reviewedAtRaw = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewedAtUtc() : null));

        OffsetDateTime reviewedAtUtc;
        if (reviewedAtRaw == null) {
            reviewedAtUtc = OffsetDateTime.now(ZoneOffset.UTC);
        } else {
            reviewedAtUtc = AnalyticsControllerSupport.parseUtcTimestamp(reviewedAtRaw);
            if (reviewedAtUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }

        String reviewNote = AnalyticsControllerSupport.truncate(
                AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewNote() : null)),
                500);
        String decision = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.decision() : null));
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

        List<String> blockedReasonsReviewed = AnalyticsControllerSupport.sanitizeStringList(
                request != null ? request.blockedReasonsReviewed() : null);
        String blockedReasonsFollowup = AnalyticsControllerSupport.truncate(
                AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.blockedReasonsFollowup() : null)),
                500);
        List<String> allowedReasons = AnalyticsControllerSupport.sanitizeStringList(
                request != null ? request.allowedReasons() : null);
        Boolean reasonCatalogRequired = request != null ? request.reasonCatalogRequired() : null;

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = AnalyticsControllerSupport.mutableDialogConfig(settings);
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
        if (allowedReasons.isEmpty()) {
            dialogConfig.remove("workspace_rollout_legacy_manual_open_allowed_reasons");
        } else {
            dialogConfig.put("workspace_rollout_legacy_manual_open_allowed_reasons", allowedReasons);
        }
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
}
