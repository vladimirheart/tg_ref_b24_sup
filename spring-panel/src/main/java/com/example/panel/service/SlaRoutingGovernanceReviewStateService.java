package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SlaRoutingGovernanceReviewStateService {

    private final SlaRoutingRuleScalarParserService scalarParserService;
    private final SlaRoutingGovernanceIssueFactoryService issueFactoryService;
    private final SlaRoutingPolicyConfigService policyConfigService;

    public SlaRoutingGovernanceReviewStateService(SlaRoutingRuleScalarParserService scalarParserService,
                                                  SlaRoutingGovernanceIssueFactoryService issueFactoryService,
                                                  SlaRoutingPolicyConfigService policyConfigService) {
        this.scalarParserService = scalarParserService;
        this.issueFactoryService = issueFactoryService;
        this.policyConfigService = policyConfigService;
    }

    public SlaRoutingGovernanceReviewStateService() {
        this(new SlaRoutingRuleScalarParserService(),
                new SlaRoutingGovernanceIssueFactoryService(),
                new SlaRoutingPolicyConfigService());
    }

    public SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation evaluate(Map<String, Object> dialogConfig,
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

        return new SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation(
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
}
