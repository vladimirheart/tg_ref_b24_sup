package com.example.panel.controller;

import com.example.panel.service.DialogAuditService;
import com.example.panel.service.SharedConfigService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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
public class AnalyticsSlaGovernanceController {

    private final DialogAuditService dialogAuditService;
    private final SharedConfigService sharedConfigService;

    public AnalyticsSlaGovernanceController(DialogAuditService dialogAuditService,
                                            SharedConfigService sharedConfigService) {
        this.dialogAuditService = dialogAuditService;
        this.sharedConfigService = sharedConfigService;
    }

    @PostMapping(value = "/sla-policy/governance-review", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateSlaPolicyGovernanceReview(
            @RequestBody(required = false) AnalyticsControllerSupport.SlaPolicyGovernanceReviewRequest request,
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
        String dryRunTicketId = AnalyticsControllerSupport.truncate(
                AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.dryRunTicketId() : null)),
                80);
        String decision = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.decision() : null));
        if (decision != null) {
            decision = decision.toLowerCase(Locale.ROOT);
            if (!"go".equals(decision) && !"hold".equals(decision)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "decision must be one of: go, hold"));
            }
        }

        String policyChangedAtRaw = AnalyticsControllerSupport.normalize(
                String.valueOf(request != null ? request.policyChangedAtUtc() : null));
        OffsetDateTime policyChangedAtUtc = null;
        if (policyChangedAtRaw != null) {
            policyChangedAtUtc = AnalyticsControllerSupport.parseUtcTimestamp(policyChangedAtRaw);
            if (policyChangedAtUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "policy_changed_at_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = AnalyticsControllerSupport.mutableDialogConfig(settings);
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
}
