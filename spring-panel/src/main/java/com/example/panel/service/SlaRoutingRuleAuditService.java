package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlaRoutingRuleAuditService {

    private final SlaRoutingRuleParserService parserService;
    private final SlaRoutingGovernanceIssueService issueService;
    private final SlaRoutingRuleBehaviorService ruleBehaviorService;
    private final SlaRoutingRuleUsageAnalysisService usageAnalysisService;
    private final SlaRoutingRuleAuditMetricsService auditMetricsService;

    @Autowired
    public SlaRoutingRuleAuditService(SlaRoutingRuleParserService parserService,
                                      SlaRoutingGovernanceIssueService issueService,
                                      SlaRoutingRuleBehaviorService ruleBehaviorService,
                                      SlaRoutingRuleUsageAnalysisService usageAnalysisService,
                                      SlaRoutingRuleAuditMetricsService auditMetricsService) {
        this.parserService = parserService;
        this.issueService = issueService;
        this.ruleBehaviorService = ruleBehaviorService;
        this.usageAnalysisService = usageAnalysisService;
        this.auditMetricsService = auditMetricsService;
    }

    public SlaRoutingRuleAuditService() {
        this(
                new SlaRoutingRuleParserService(),
                new SlaRoutingGovernanceIssueService(),
                new SlaRoutingRuleBehaviorService(),
                new SlaRoutingRuleUsageAnalysisService(),
                new SlaRoutingRuleAuditMetricsService()
        );
    }

    public RoutingAuditAnalysis analyze(List<Map<String, Object>> criticalCandidates,
                                        Object rawRules,
                                        Instant generatedAt,
                                        int broadCoveragePct,
                                        boolean requireLayers,
                                        boolean requireOwner,
                                        boolean requireReview,
                                        boolean blockOnConflict,
                                        long reviewTtlHours) {
        List<Map<String, Object>> safeCandidates = criticalCandidates == null ? List.of() : criticalCandidates;
        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> definitions = parserService.parseDefinitions(rawRules);
        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> rules = new ArrayList<>();
        SlaRoutingRuleUsageAnalysisService.RuleUsageAnalysis usageAnalysis = usageAnalysisService.analyze(safeCandidates, definitions);

        for (SlaRoutingRuleTypes.AutoAssignRuleDefinition definition : definitions) {
            SlaRoutingRuleUsageAnalysisService.RuleUsageStats usageStats = usageAnalysis.usageStatsByRoute()
                    .getOrDefault(definition.ruleId(), new SlaRoutingRuleUsageAnalysisService.RuleUsageStats());
            int matchedCount = usageStats.matchedTickets().size();
            int selectedCount = usageStats.selectedTickets().size();
            double coverageRate = safeCandidates.isEmpty() ? 0d : (double) matchedCount / safeCandidates.size();
            boolean broadRule = !safeCandidates.isEmpty()
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

        SlaRoutingRuleAuditMetricsService.AuditMetrics metrics =
                auditMetricsService.summarize(issues, rules, usageAnalysis.conflictTicketsByRoute());

        return new RoutingAuditAnalysis(definitions.size(), issues, rules, usageAnalysis.layerCounts(), metrics.decisionsByLayer(),
                metrics.decisionsByRoute(), usageAnalysis.conflictTicketsByRoute(), metrics.conflictingRulesCount(),
                metrics.conflictingTicketsCount(), metrics.mandatoryIssueTotal(), metrics.advisoryIssueTotal(),
                metrics.conflictIssueTotal(), metrics.reviewIssueTotal(), metrics.ownershipIssueTotal());
    }

    public record RoutingAuditAnalysis(int rulesTotal,
                                       List<Map<String, Object>> issues,
                                       List<Map<String, Object>> rules,
                                       Map<String, Integer> layerCounts,
                                       Map<String, Long> decisionsByLayer,
                                       Map<String, Long> decisionsByRoute,
                                       Map<String, Set<String>> conflictTicketsByRoute,
                                       long conflictingRulesCount,
                                       long conflictingTicketsCount,
                                       long mandatoryIssueTotal,
                                       long advisoryIssueTotal,
                                       long conflictIssueTotal,
                                       long reviewIssueTotal,
                                       long ownershipIssueTotal) {
    }

}
