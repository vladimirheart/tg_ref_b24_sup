package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SlaRoutingRuleAuditService {

    private final SlaRoutingRuleParserService parserService;
    private final SlaRoutingRuleUsageAnalysisService usageAnalysisService;
    private final SlaRoutingRuleAuditEvaluationService evaluationService;
    private final SlaRoutingRuleAuditMetricsService auditMetricsService;

    public SlaRoutingRuleAuditService(SlaRoutingRuleParserService parserService,
                                      SlaRoutingRuleUsageAnalysisService usageAnalysisService,
                                      SlaRoutingRuleAuditEvaluationService evaluationService,
                                      SlaRoutingRuleAuditMetricsService auditMetricsService) {
        this.parserService = parserService;
        this.usageAnalysisService = usageAnalysisService;
        this.evaluationService = evaluationService;
        this.auditMetricsService = auditMetricsService;
    }

    public SlaRoutingRuleAuditService() {
        this(
                new SlaRoutingRuleParserService(),
                new SlaRoutingRuleUsageAnalysisService(),
                new SlaRoutingRuleAuditEvaluationService(),
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
        SlaRoutingRuleUsageAnalysisService.RuleUsageAnalysis usageAnalysis = usageAnalysisService.analyze(safeCandidates, definitions);
        SlaRoutingRuleAuditEvaluationService.RuleEvaluationBundle evaluationBundle = evaluationService.evaluate(
                definitions,
                usageAnalysis,
                safeCandidates.size(),
                broadCoveragePct,
                requireLayers,
                requireOwner,
                requireReview,
                blockOnConflict,
                reviewTtlHours,
                generatedAt
        );

        SlaRoutingRuleAuditMetricsService.AuditMetrics metrics =
                auditMetricsService.summarize(evaluationBundle.issues(), evaluationBundle.rules(), usageAnalysis.conflictTicketsByRoute());

        return new RoutingAuditAnalysis(definitions.size(), evaluationBundle.issues(), evaluationBundle.rules(), usageAnalysis.layerCounts(), metrics.decisionsByLayer(),
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
