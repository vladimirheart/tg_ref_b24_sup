package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingGovernanceReviewIssueService {

    private final SlaRoutingGovernanceIssueFactoryService issueFactoryService;

    public SlaRoutingGovernanceReviewIssueService(SlaRoutingGovernanceIssueFactoryService issueFactoryService) {
        this.issueFactoryService = issueFactoryService;
    }

    public SlaRoutingGovernanceReviewIssueService() {
        this(new SlaRoutingGovernanceIssueFactoryService());
    }

    public List<Map<String, Object>> collect(SlaRoutingGovernanceReviewDecisionService.GovernanceReviewDecisionState decisionState,
                                             Instant generatedAt,
                                             int conflictingRulesCount,
                                             int conflictingTicketsCount,
                                             boolean blockOnConflict,
                                             String governanceReviewPath) {
        List<Map<String, Object>> issues = new ArrayList<>();
        if (decisionState.governanceReviewedAtInvalid()) {
            issues.add(issueFactoryService.buildGovernanceIssue(decisionState.governanceReviewRequired() ? "rollout_blocker" : "backlog_candidate",
                    decisionState.governanceReviewRequired() ? "hold" : "attention", "governance_review_invalid_utc", "governance_review",
                    "UTC timestamp в SLA governance review невалиден.", "reviewed_at=invalid", List.of(), List.of()));
        } else if (decisionState.governanceReviewRequired() && decisionState.policyChangedAfterReview()) {
            issues.add(issueFactoryService.buildGovernanceIssue("rollout_blocker", "hold", "governance_review_outdated_after_policy_change", "governance_review",
                    "SLA policy менялась после последнего governance review и требует нового решения.",
                    "policy_changed_at=%s".formatted(decisionState.policyChangedAt()), List.of(), List.of()));
        } else if (decisionState.governanceReviewRequired() && !decisionState.governanceReviewPresent()) {
            issues.add(issueFactoryService.buildGovernanceIssue("rollout_blocker", "hold", "governance_review_missing", "governance_review",
                    "SLA policy governance review обязателен, но ещё не подтверждён.", "reviewed_by/reviewed_at missing", List.of(), List.of()));
        } else if (decisionState.governanceReviewRequired() && !decisionState.governanceReviewFresh()) {
            issues.add(issueFactoryService.buildGovernanceIssue("rollout_blocker", "hold", "governance_review_stale", "governance_review",
                    "SLA policy governance review устарел и требует обновления.",
                    "review_age_hours=%d > ttl=%d".formatted(decisionState.governanceReviewAgeHours(), decisionState.governanceReviewTtlHours()), List.of(), List.of()));
        }
        if (decisionState.policyChangedAtInvalid()) {
            issues.add(issueFactoryService.buildGovernanceIssue(decisionState.governanceReviewRequired() ? "rollout_blocker" : "backlog_candidate",
                    decisionState.governanceReviewRequired() ? "hold" : "attention", "governance_policy_changed_at_invalid_utc", "governance_review",
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
        if (decisionState.governanceDryRunTicketRequired() && decisionState.governanceDryRunTicketId() == null) {
            issues.add(issueFactoryService.buildGovernanceIssue(decisionState.governanceReviewRequired() ? "rollout_blocker" : "backlog_candidate",
                    decisionState.governanceReviewRequired() ? "hold" : "attention", "governance_dry_run_ticket_missing", "governance_review",
                    "Для SLA policy review нужен ticket-id dry-run проверки.", "dry_run_ticket_id=missing", List.of(), List.of()));
        }
        if (decisionState.governanceDecisionRequired() && decisionState.governanceDecision() == null) {
            issues.add(issueFactoryService.buildGovernanceIssue(decisionState.governanceReviewRequired() ? "rollout_blocker" : "backlog_candidate",
                    decisionState.governanceReviewRequired() ? "hold" : "attention", "governance_decision_missing", "governance_review",
                    "Для SLA policy governance review нужно явно зафиксировать decision (go/hold).", "decision=missing", List.of(), List.of()));
        } else if ("hold".equals(decisionState.governanceDecision())) {
            issues.add(issueFactoryService.buildGovernanceIssue("rollout_blocker", "hold", "governance_decision_hold", "governance_review",
                    "SLA policy governance decision зафиксирован как hold.", "decision=hold", List.of(), List.of()));
        }
        return issues;
    }
}
