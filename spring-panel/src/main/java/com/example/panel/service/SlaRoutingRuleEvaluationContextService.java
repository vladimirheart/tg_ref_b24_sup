package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class SlaRoutingRuleEvaluationContextService {

    private final SlaRoutingRuleBehaviorService ruleBehaviorService;
    private final SlaRoutingRuleWinnerSelectionService winnerSelectionService;

    public SlaRoutingRuleEvaluationContextService(SlaRoutingRuleBehaviorService ruleBehaviorService,
                                                  SlaRoutingRuleWinnerSelectionService winnerSelectionService) {
        this.ruleBehaviorService = ruleBehaviorService;
        this.winnerSelectionService = winnerSelectionService;
    }

    public SlaRoutingRuleEvaluationContextService() {
        this(new SlaRoutingRuleBehaviorService(), new SlaRoutingRuleWinnerSelectionService());
    }

    public RuleEvaluationContext build(SlaRoutingRuleTypes.AutoAssignRuleDefinition definition,
                                       SlaRoutingRuleUsageAnalysisService.RuleUsageAnalysis usageAnalysis,
                                       int candidateTotal,
                                       int broadCoveragePct) {
        Map<String, SlaRoutingRuleUsageAnalysisService.RuleUsageStats> usageStatsByRoute =
                usageAnalysis == null ? Map.of() : usageAnalysis.usageStatsByRoute();
        Map<String, Set<String>> conflictTicketsByRoute =
                usageAnalysis == null ? Map.of() : usageAnalysis.conflictTicketsByRoute();
        Map<String, Set<String>> tiedRoutesByRoute =
                usageAnalysis == null ? Map.of() : usageAnalysis.tiedRoutesByRoute();

        SlaRoutingRuleUsageAnalysisService.RuleUsageStats usageStats = usageStatsByRoute
                .getOrDefault(definition.ruleId(), new SlaRoutingRuleUsageAnalysisService.RuleUsageStats());
        int matchedCount = usageStats.matchedTickets().size();
        int selectedCount = usageStats.selectedTickets().size();
        double coverageRate = candidateTotal == 0 ? 0d : (double) matchedCount / candidateTotal;
        boolean broadRule = candidateTotal > 0
                && coverageRate >= (broadCoveragePct / 100d)
                && ruleBehaviorService.specificityScore(definition.rule()) <= 2;
        Set<String> conflictTickets = conflictTicketsByRoute.getOrDefault(definition.ruleId(), Set.of());
        Set<String> tiedRoutes = tiedRoutesByRoute.getOrDefault(definition.ruleId(), Set.of());

        return new RuleEvaluationContext(
                matchedCount,
                selectedCount,
                coverageRate,
                broadRule,
                !conflictTickets.isEmpty(),
                conflictTickets,
                tiedRoutes,
                winnerSelectionService.formatRuleAssigneeTarget(definition.rule())
        );
    }

    public record RuleEvaluationContext(int matchedCount,
                                        int selectedCount,
                                        double coverageRate,
                                        boolean broadRule,
                                        boolean hasConflict,
                                        Set<String> conflictTickets,
                                        Set<String> tiedRoutes,
                                        String assigneeTarget) {
    }
}
