package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SlaRoutingRuleAuditEvaluationService {

    private final SlaRoutingGovernanceIssueService issueService;
    private final SlaRoutingRuleBehaviorService ruleBehaviorService;
    private final SlaRoutingRuleParserService parserService;

    public SlaRoutingRuleAuditEvaluationService(SlaRoutingGovernanceIssueService issueService,
                                                SlaRoutingRuleBehaviorService ruleBehaviorService,
                                                SlaRoutingRuleParserService parserService) {
        this.issueService = issueService;
        this.ruleBehaviorService = ruleBehaviorService;
        this.parserService = parserService;
    }

    public SlaRoutingRuleAuditEvaluationService() {
        this(new SlaRoutingGovernanceIssueService(), new SlaRoutingRuleBehaviorService(), new SlaRoutingRuleParserService());
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
            SlaRoutingRuleUsageAnalysisService.RuleUsageStats usageStats = usageAnalysis.usageStatsByRoute()
                    .getOrDefault(definition.ruleId(), new SlaRoutingRuleUsageAnalysisService.RuleUsageStats());
            int matchedCount = usageStats.matchedTickets().size();
            int selectedCount = usageStats.selectedTickets().size();
            double coverageRate = candidateTotal == 0 ? 0d : (double) matchedCount / candidateTotal;
            boolean broadRule = candidateTotal > 0
                    && coverageRate >= (broadCoveragePct / 100d)
                    && ruleBehaviorService.specificityScore(definition.rule()) <= 2;
            SlaRoutingGovernanceIssueService.RuleGovernanceEvaluation evaluation = issueService.evaluateRule(
                    definition,
                    matchedCount,
                    selectedCount,
                    coverageRate,
                    broadRule,
                    requireLayers,
                    requireOwner,
                    requireReview,
                    blockOnConflict,
                    reviewTtlHours,
                    generatedAt,
                    usageAnalysis.conflictTicketsByRoute().containsKey(definition.ruleId()),
                    usageAnalysis.conflictTicketsByRoute().getOrDefault(definition.ruleId(), Set.of()),
                    usageAnalysis.tiedRoutesByRoute().getOrDefault(definition.ruleId(), Set.of()),
                    parserService.formatRuleAssigneeTarget(definition.rule())
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
