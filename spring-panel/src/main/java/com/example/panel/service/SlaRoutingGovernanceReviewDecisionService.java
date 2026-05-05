package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class SlaRoutingGovernanceReviewDecisionService {

    private final SlaRoutingRuleScalarParserService scalarParserService;
    private final SlaRoutingPolicyConfigService policyConfigService;

    public SlaRoutingGovernanceReviewDecisionService(SlaRoutingRuleScalarParserService scalarParserService,
                                                     SlaRoutingPolicyConfigService policyConfigService) {
        this.scalarParserService = scalarParserService;
        this.policyConfigService = policyConfigService;
    }

    public SlaRoutingGovernanceReviewDecisionService() {
        this(new SlaRoutingRuleScalarParserService(), new SlaRoutingPolicyConfigService());
    }

    public GovernanceReviewDecisionState evaluate(Map<String, Object> dialogConfig,
                                                  Instant generatedAt,
                                                  String governanceReviewPath) {
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
        String governanceDecision = normalizeDecision(dialogConfig.get("sla_critical_auto_assign_governance_decision"));

        boolean governanceDryRunReady = !governanceDryRunTicketRequired || governanceDryRunTicketId != null;
        boolean governanceDecisionReady = !governanceDecisionRequired || governanceDecision != null;
        boolean governanceReviewReady = !governanceReviewRequired
                || (governanceReviewPresent && governanceReviewFresh && !governanceReviewedAtInvalid
                && governanceDryRunReady && governanceDecisionReady && !"hold".equals(governanceDecision)
                && !policyChangedAfterReview && !policyChangedAtInvalid);

        return new GovernanceReviewDecisionState(
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
                governanceReviewReady
        );
    }

    private String normalizeDecision(Object rawDecision) {
        String governanceDecision = scalarParserService.trimToNull(String.valueOf(rawDecision));
        if (governanceDecision == null) {
            return null;
        }
        governanceDecision = governanceDecision.toLowerCase(Locale.ROOT);
        return "go".equals(governanceDecision) || "hold".equals(governanceDecision) ? governanceDecision : null;
    }

    public record GovernanceReviewDecisionState(
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
            boolean governanceReviewReady
    ) {
    }
}
