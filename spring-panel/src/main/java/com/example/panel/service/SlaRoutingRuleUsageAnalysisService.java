package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlaRoutingRuleUsageAnalysisService {

    private final SlaRoutingRuleDefinitionMatchService definitionMatchService;
    private final SlaRoutingRuleWinnerSelectionService winnerSelectionService;
    private final SlaRoutingRuleCandidateContextService candidateContextService;

    @Autowired
    public SlaRoutingRuleUsageAnalysisService(SlaRoutingRuleDefinitionMatchService definitionMatchService,
                                              SlaRoutingRuleWinnerSelectionService winnerSelectionService,
                                              SlaRoutingRuleCandidateContextService candidateContextService) {
        this.definitionMatchService = definitionMatchService;
        this.winnerSelectionService = winnerSelectionService;
        this.candidateContextService = candidateContextService;
    }

    public SlaRoutingRuleUsageAnalysisService() {
        this(new SlaRoutingRuleDefinitionMatchService(), new SlaRoutingRuleWinnerSelectionService(),
                new SlaRoutingRuleCandidateContextService());
    }

    public RuleUsageAnalysis analyze(List<Map<String, Object>> criticalCandidates,
                                     List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> definitions) {
        List<Map<String, Object>> safeCandidates = criticalCandidates == null ? List.of() : criticalCandidates;
        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> safeDefinitions = definitions == null ? List.of() : definitions;
        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> activeDefinitions = safeDefinitions.stream()
                .filter(definition -> definition.rule() != null)
                .toList();

        Map<String, RuleUsageStats> usageStatsByRoute = new LinkedHashMap<>();
        Map<String, Set<String>> conflictTicketsByRoute = new LinkedHashMap<>();
        Map<String, Set<String>> tiedRoutesByRoute = new LinkedHashMap<>();
        Map<String, Integer> layerCounts = new LinkedHashMap<>();

        for (SlaRoutingRuleTypes.AutoAssignRuleDefinition definition : safeDefinitions) {
            layerCounts.merge(definition.layer(), 1, Integer::sum);
            usageStatsByRoute.put(definition.ruleId(), new RuleUsageStats());
        }

        for (Map<String, Object> candidate : safeCandidates) {
            String ticketId = candidateContextService.ticketId(candidate);
            List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> matchedDefinitions = activeDefinitions.stream()
                    .filter(definition -> definitionMatchService.matchesDefinition(definition, candidate))
                    .toList();
            matchedDefinitions.forEach(definition -> usageStatsByRoute.get(definition.ruleId()).matchedTickets().add(ticketId));
            List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> winners = winnerSelectionService.resolveWinningDefinitions(matchedDefinitions);
            if (!winners.isEmpty()) {
                winners.forEach(definition -> usageStatsByRoute.get(definition.ruleId()).selectedTickets().add(ticketId));
            }
            if (winners.size() > 1) {
                Set<String> winnerRouteIds = winners.stream()
                        .map(SlaRoutingRuleTypes.AutoAssignRuleDefinition::ruleId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                for (SlaRoutingRuleTypes.AutoAssignRuleDefinition winner : winners) {
                    conflictTicketsByRoute.computeIfAbsent(winner.ruleId(), key -> new LinkedHashSet<>()).add(ticketId);
                    tiedRoutesByRoute.computeIfAbsent(winner.ruleId(), key -> new LinkedHashSet<>()).addAll(
                            winnerRouteIds.stream().filter(routeId -> !winner.ruleId().equals(routeId)).toList());
                }
            }
        }

        return new RuleUsageAnalysis(usageStatsByRoute, conflictTicketsByRoute, tiedRoutesByRoute, layerCounts);
    }

    public record RuleUsageAnalysis(Map<String, RuleUsageStats> usageStatsByRoute,
                                    Map<String, Set<String>> conflictTicketsByRoute,
                                    Map<String, Set<String>> tiedRoutesByRoute,
                                    Map<String, Integer> layerCounts) {
    }

    public record RuleUsageStats(Set<String> matchedTickets, Set<String> selectedTickets) {
        public RuleUsageStats() {
            this(new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }
}
