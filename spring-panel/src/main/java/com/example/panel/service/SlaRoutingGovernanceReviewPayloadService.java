package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SlaRoutingGovernanceReviewPayloadService {

    public Map<String, Object> buildRequirementsPayload(SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation review,
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

    public Map<String, Object> buildGovernanceReviewPayload(SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation review,
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
}
