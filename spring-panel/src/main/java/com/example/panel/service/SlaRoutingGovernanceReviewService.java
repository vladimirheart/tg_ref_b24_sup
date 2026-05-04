package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SlaRoutingGovernanceReviewService {

    private final SlaRoutingRuleScalarParserService scalarParserService;
    private final SlaRoutingGovernanceIssueFactoryService issueFactoryService;
    private final SlaRoutingPolicyConfigService policyConfigService;

    public SlaRoutingGovernanceReviewService(SlaRoutingRuleScalarParserService scalarParserService,
                                             SlaRoutingGovernanceIssueFactoryService issueFactoryService,
                                             SlaRoutingPolicyConfigService policyConfigService) {
        this.scalarParserService = scalarParserService;
        this.issueFactoryService = issueFactoryService;
        this.policyConfigService = policyConfigService;
    }

    public SlaRoutingGovernanceReviewService() {
        this(new SlaRoutingRuleScalarParserService(),
                new SlaRoutingGovernanceIssueFactoryService(),
                new SlaRoutingPolicyConfigService());
    }

    public GovernanceReviewEvaluation evaluate(Map<String, Object> dialogConfig,
                                               Instant generatedAt,
                                               int conflictingRulesCount,
                                               int conflictingTicketsCount,
                                               boolean blockOnConflict) {
        String governanceReviewPath = resolveGovernanceReviewPath(dialogConfig.get("sla_critical_auto_assign_governance_review_path"));
        boolean governanceReviewRequiredConfigured = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_governance_review_required", false);
        boolean governanceDryRunTicketRequiredConfigured = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_governance_dry_run_ticket_required", false);
        boolean governanceDecisionRequiredConfigured = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_governance_decision_required", false);
        long governanceReviewTtlHours = Math.max(1L, policyConfigService.resolvePositiveInt(dialogConfig, "sla_critical_auto_assign_governance_review_ttl_hours", 168, 24 * 90));
        boolean governanceReviewRequired = governanceReviewRequiredConfigured || !"custom".equals(governanceReviewPath);
        boolean governanceDryRunTicketRequired = governanceDryRunTicketRequiredConfigured || "strict".equals(governanceReviewPath);
        boolean governanceDecisionRequired = governanceDecisionRequiredConfigured
                || "standard".equals(governanceReviewPath)
                || "strict".equals(governanceReviewPath);

        String governanceReviewedBy = scalarParserService.trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_reviewed_by")));
        String governanceReviewedAtRaw = scalarParserService.trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_reviewed_at")));
        Instant governanceReviewedAt = scalarParserService.parseUtcInstant(governanceReviewedAtRaw);
        boolean governanceReviewedAtInvalid = governanceReviewedAtRaw != null && governanceReviewedAt == null;
        long governanceReviewAgeHours = governanceReviewedAt != null ? Math.max(0L, Duration.between(governanceReviewedAt, generatedAt).toHours()) : -1L;
        boolean governanceReviewFresh = governanceReviewedAt != null && governanceReviewAgeHours <= governanceReviewTtlHours;
        boolean governanceReviewPresent = governanceReviewedAt != null && governanceReviewedBy != null;

        String policyChangedAtRaw = scalarParserService.trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_policy_changed_at")));
        Instant policyChangedAt = scalarParserService.parseUtcInstant(policyChangedAtRaw);
        boolean policyChangedAtInvalid = policyChangedAtRaw != null && policyChangedAt == null;
        long policyDecisionLeadTimeHours = policyChangedAt != null && governanceReviewedAt != null
                ? Math.max(0L, Duration.between(policyChangedAt, governanceReviewedAt).toHours()) : -1L;
        long policyDecisionLeadTimeActiveHours = policyChangedAt != null && governanceReviewedAt == null
                ? Math.max(0L, Duration.between(policyChangedAt, generatedAt).toHours()) : -1L;
        boolean policyChangedAfterReview = policyChangedAt != null && (governanceReviewedAt == null || policyChangedAt.isAfter(governanceReviewedAt));

        String governanceReviewNote = scalarParserService.trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_review_note")));
        String governanceDryRunTicketId = scalarParserService.trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_dry_run_ticket_id")));
        String governanceDecision = scalarParserService.trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_decision")));
        if (governanceDecision != null) {
            governanceDecision = governanceDecision.toLowerCase(Locale.ROOT);
            if (!"go".equals(governanceDecision) && !"hold".equals(governanceDecision)) {
                governanceDecision = null;
            }
        }

        boolean governanceDryRunReady = !governanceDryRunTicketRequired || governanceDryRunTicketId != null;
        boolean governanceDecisionReady = !governanceDecisionRequired || governanceDecision != null;
        boolean governanceReviewReady = !governanceReviewRequired
                || (governanceReviewPresent && governanceReviewFresh && !governanceReviewedAtInvalid
                && governanceDryRunReady && governanceDecisionReady && !"hold".equals(governanceDecision)
                && !policyChangedAfterReview && !policyChangedAtInvalid);

        List<Map<String, Object>> issues = new ArrayList<>();
        if (governanceReviewedAtInvalid) {
            issues.add(issueFactoryService.buildGovernanceIssue(governanceReviewRequired ? "rollout_blocker" : "backlog_candidate",
                    governanceReviewRequired ? "hold" : "attention", "governance_review_invalid_utc", "governance_review",
                    "UTC timestamp в SLA governance review невалиден.", "reviewed_at=invalid", List.of(), List.of()));
        } else if (governanceReviewRequired && policyChangedAfterReview) {
            issues.add(issueFactoryService.buildGovernanceIssue("rollout_blocker", "hold", "governance_review_outdated_after_policy_change", "governance_review",
                    "SLA policy менялась после последнего governance review и требует нового решения.",
                    "policy_changed_at=%s".formatted(policyChangedAt), List.of(), List.of()));
        } else if (governanceReviewRequired && !governanceReviewPresent) {
            issues.add(issueFactoryService.buildGovernanceIssue("rollout_blocker", "hold", "governance_review_missing", "governance_review",
                    "SLA policy governance review обязателен, но ещё не подтверждён.", "reviewed_by/reviewed_at missing", List.of(), List.of()));
        } else if (governanceReviewRequired && !governanceReviewFresh) {
            issues.add(issueFactoryService.buildGovernanceIssue("rollout_blocker", "hold", "governance_review_stale", "governance_review",
                    "SLA policy governance review устарел и требует обновления.",
                    "review_age_hours=%d > ttl=%d".formatted(governanceReviewAgeHours, governanceReviewTtlHours), List.of(), List.of()));
        }
        if (policyChangedAtInvalid) {
            issues.add(issueFactoryService.buildGovernanceIssue(governanceReviewRequired ? "rollout_blocker" : "backlog_candidate",
                    governanceReviewRequired ? "hold" : "attention", "governance_policy_changed_at_invalid_utc", "governance_review",
                    "UTC timestamp последнего SLA policy change невалиден.", "policy_changed_at=invalid", List.of(), List.of()));
        }
        if (conflictingRulesCount > 0) {
            String preReviewConflictStatus = blockOnConflict || "strict".equals(governanceReviewPath) ? "hold" : "attention";
            issues.add(issueFactoryService.buildGovernanceIssue("hold".equals(preReviewConflictStatus) ? "rollout_blocker" : "backlog_candidate",
                    preReviewConflictStatus, "governance_pre_review_conflicts_detected", "governance_review",
                    "До финального SLA review остаются конфликтующие routing rules.",
                    "rules=%d, tickets=%d".formatted(conflictingRulesCount, conflictingTicketsCount),
                    List.of(), List.of()));
        }
        if (governanceDryRunTicketRequired && governanceDryRunTicketId == null) {
            issues.add(issueFactoryService.buildGovernanceIssue(governanceReviewRequired ? "rollout_blocker" : "backlog_candidate",
                    governanceReviewRequired ? "hold" : "attention", "governance_dry_run_ticket_missing", "governance_review",
                    "Для SLA policy review нужен ticket-id dry-run проверки.", "dry_run_ticket_id=missing", List.of(), List.of()));
        }
        if (governanceDecisionRequired && governanceDecision == null) {
            issues.add(issueFactoryService.buildGovernanceIssue(governanceReviewRequired ? "rollout_blocker" : "backlog_candidate",
                    governanceReviewRequired ? "hold" : "attention", "governance_decision_missing", "governance_review",
                    "Для SLA policy governance review нужно явно зафиксировать decision (go/hold).", "decision=missing", List.of(), List.of()));
        } else if ("hold".equals(governanceDecision)) {
            issues.add(issueFactoryService.buildGovernanceIssue("rollout_blocker", "hold", "governance_decision_hold", "governance_review",
                    "SLA policy governance decision зафиксирован как hold.", "decision=hold", List.of(), List.of()));
        }

        return new GovernanceReviewEvaluation(
                governanceReviewPath,
                governanceReviewRequiredConfigured,
                governanceDryRunTicketRequiredConfigured,
                governanceDecisionRequiredConfigured,
                governanceReviewTtlHours,
                governanceReviewRequired,
                governanceDryRunTicketRequired,
                governanceDecisionRequired,
                governanceReviewedBy,
                governanceReviewedAt,
                governanceReviewedAtInvalid,
                governanceReviewAgeHours,
                governanceReviewFresh,
                governanceReviewPresent,
                policyChangedAt,
                policyChangedAtInvalid,
                policyDecisionLeadTimeHours,
                policyDecisionLeadTimeActiveHours,
                policyChangedAfterReview,
                governanceReviewNote,
                governanceDryRunTicketId,
                governanceDecision,
                governanceDryRunReady,
                governanceDecisionReady,
                governanceReviewReady,
                issues
        );
    }

    public Map<String, Object> buildRequirementsPayload(GovernanceReviewEvaluation review,
                                                        boolean requireLayers,
                                                        boolean requireOwner,
                                                        boolean requireReview,
                                                        long reviewTtlHours,
                                                        boolean blockOnConflict,
                                                        int broadCoveragePct,
                                                        int conflictingRulesCount) {
        return Map.ofEntries(
                Map.entry("require_layers", requireLayers),
                Map.entry("require_owner", requireOwner),
                Map.entry("require_review", requireReview),
                Map.entry("review_ttl_hours", reviewTtlHours),
                Map.entry("block_on_conflicts", blockOnConflict),
                Map.entry("broad_rule_coverage_pct", broadCoveragePct),
                Map.entry("governance_review_path", review.governanceReviewPath()),
                Map.entry("governance_review_required", review.governanceReviewRequired()),
                Map.entry("governance_review_required_configured", review.governanceReviewRequiredConfigured()),
                Map.entry("governance_review_ttl_hours", review.governanceReviewTtlHours()),
                Map.entry("governance_dry_run_ticket_required", review.governanceDryRunTicketRequired()),
                Map.entry("governance_dry_run_ticket_required_configured", review.governanceDryRunTicketRequiredConfigured()),
                Map.entry("governance_decision_required", review.governanceDecisionRequired()),
                Map.entry("governance_decision_required_configured", review.governanceDecisionRequiredConfigured()),
                Map.entry("governance_pre_review_conflicts_detected", conflictingRulesCount > 0)
        );
    }

    public Map<String, Object> buildGovernanceReviewPayload(GovernanceReviewEvaluation review,
                                                            int conflictingRulesCount,
                                                            int conflictingTicketsCount) {
        return Map.ofEntries(
                Map.entry("review_path", review.governanceReviewPath()),
                Map.entry("required", review.governanceReviewRequired()),
                Map.entry("ready", review.governanceReviewReady()),
                Map.entry("reviewed_by", review.governanceReviewedBy() == null ? "" : review.governanceReviewedBy()),
                Map.entry("reviewed_at_utc", review.governanceReviewedAt() != null ? review.governanceReviewedAt().toString() : ""),
                Map.entry("reviewed_at_invalid_utc", review.governanceReviewedAtInvalid()),
                Map.entry("policy_changed_at_utc", review.policyChangedAt() != null ? review.policyChangedAt().toString() : ""),
                Map.entry("policy_changed_at_invalid_utc", review.policyChangedAtInvalid()),
                Map.entry("policy_changed_after_review", review.policyChangedAfterReview()),
                Map.entry("review_note", review.governanceReviewNote() == null ? "" : review.governanceReviewNote()),
                Map.entry("dry_run_ticket_id", review.governanceDryRunTicketId() == null ? "" : review.governanceDryRunTicketId()),
                Map.entry("dry_run_ticket_required", review.governanceDryRunTicketRequired()),
                Map.entry("decision_required", review.governanceDecisionRequired()),
                Map.entry("decision_ready", review.governanceDecisionReady()),
                Map.entry("decision", review.governanceDecision() == null ? "" : review.governanceDecision()),
                Map.entry("review_ttl_hours", review.governanceReviewTtlHours()),
                Map.entry("review_age_hours", review.governanceReviewAgeHours()),
                Map.entry("decision_lead_time_hours", review.policyDecisionLeadTimeHours()),
                Map.entry("decision_lead_time_active_hours", review.policyDecisionLeadTimeActiveHours()),
                Map.entry("pre_review_conflicts_detected", conflictingRulesCount > 0),
                Map.entry("pre_review_conflicting_rules", conflictingRulesCount),
                Map.entry("pre_review_conflicting_tickets", conflictingTicketsCount)
        );
    }

    public String resolveGovernanceReviewPath(Object rawValue) {
        String normalized = scalarParserService.trimToNull(String.valueOf(rawValue));
        if (normalized == null) return "custom";
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "light" -> "light";
            case "standard" -> "standard";
            case "strict" -> "strict";
            default -> "custom";
        };
    }

    public record GovernanceReviewEvaluation(
            String governanceReviewPath,
            boolean governanceReviewRequiredConfigured,
            boolean governanceDryRunTicketRequiredConfigured,
            boolean governanceDecisionRequiredConfigured,
            long governanceReviewTtlHours,
            boolean governanceReviewRequired,
            boolean governanceDryRunTicketRequired,
            boolean governanceDecisionRequired,
            String governanceReviewedBy,
            Instant governanceReviewedAt,
            boolean governanceReviewedAtInvalid,
            long governanceReviewAgeHours,
            boolean governanceReviewFresh,
            boolean governanceReviewPresent,
            Instant policyChangedAt,
            boolean policyChangedAtInvalid,
            long policyDecisionLeadTimeHours,
            long policyDecisionLeadTimeActiveHours,
            boolean policyChangedAfterReview,
            String governanceReviewNote,
            String governanceDryRunTicketId,
            String governanceDecision,
            boolean governanceDryRunReady,
            boolean governanceDecisionReady,
            boolean governanceReviewReady,
            List<Map<String, Object>> issues
    ) {
    }
}
