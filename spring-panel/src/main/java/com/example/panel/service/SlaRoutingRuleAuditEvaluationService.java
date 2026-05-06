package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingRuleAuditEvaluationService {

    private final SlaRoutingGovernanceIssueService issueService;
    private final SlaRoutingRuleEvaluationContextService evaluationContextService;

    public SlaRoutingRuleAuditEvaluationService(SlaRoutingGovernanceIssueService issueService,
                                                SlaRoutingRuleEvaluationContextService evaluationContextService) {
        this.issueService = issueService;
        this.evaluationContextService = evaluationContextService;
    }

    public SlaRoutingRuleAuditEvaluationService() {
        this(new SlaRoutingGovernanceIssueService(), new SlaRoutingRuleEvaluationContextService());
    }

    public RuleEvaluationBundle evaluate(List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> definitions,
                                         SlaRoutingRuleUsageAnalysisService.RuleUsageAnalysis usageAnalysis,
                                         int candidateTotal,
                                         int broadCoveragePct,
                                         boolean requireLayers,
                                         boolean requireOwner,
                                         boolean requireReview,
                                         boolean blockOnConflict,
                                         long reviewTtlHours,
                                         Instant generatedAt) {
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> rules = new ArrayList<>();
        for (SlaRoutingRuleTypes.AutoAssignRuleDefinition definition : definitions) {
            SlaRoutingRuleEvaluationContextService.RuleEvaluationContext context = evaluationContextService.build(
                    definition,
                    usageAnalysis,
                    candidateTotal,
                    broadCoveragePct
            );
            SlaRoutingGovernanceIssueService.RuleGovernanceEvaluation evaluation = issueService.evaluateRule(
                    definition,
                    context.matchedCount(),
                    context.selectedCount(),
                    context.coverageRate(),
                    context.broadRule(),
                    requireLayers,
                    requireOwner,
                    requireReview,
                    blockOnConflict,
                    reviewTtlHours,
                    generatedAt,
                    context.hasConflict(),
                    context.conflictTickets(),
                    context.tiedRoutes(),
                    context.assigneeTarget()
            );
            issues.addAll(evaluation.emittedIssues());
            rules.add(evaluation.rulePayload());
        }
        return new RuleEvaluationBundle(issues, rules);
    }

    public record RuleEvaluationBundle(List<Map<String, Object>> issues,
                                       List<Map<String, Object>> rules) {
    }
}
