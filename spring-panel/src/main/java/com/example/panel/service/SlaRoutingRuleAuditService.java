package com.example.panel.service;

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

    public SlaRoutingRuleAuditService(SlaRoutingRuleParserService parserService,
                                      SlaRoutingGovernanceIssueService issueService) {
        this.parserService = parserService;
        this.issueService = issueService;
    }

    public SlaRoutingRuleAuditService() {
        this(new SlaRoutingRuleParserService(), new SlaRoutingGovernanceIssueService());
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
        List<SlaRoutingRuleParserService.AutoAssignRuleDefinition> definitions = parserService.parseDefinitions(rawRules);
        List<SlaRoutingRuleParserService.AutoAssignRuleDefinition> activeDefinitions = definitions.stream()
                .filter(definition -> definition.rule() != null)
                .toList();

        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> rules = new ArrayList<>();
        Map<String, RuleUsageStats> usageStatsByRoute = new LinkedHashMap<>();
        Map<String, Set<String>> conflictTicketsByRoute = new LinkedHashMap<>();
        Map<String, Set<String>> tiedRoutesByRoute = new LinkedHashMap<>();
        Map<String, Integer> layerCounts = new LinkedHashMap<>();

        for (SlaRoutingRuleParserService.AutoAssignRuleDefinition definition : definitions) {
            layerCounts.merge(definition.layer(), 1, Integer::sum);
            usageStatsByRoute.put(definition.ruleId(), new RuleUsageStats());
        }

        for (Map<String, Object> candidate : safeCandidates) {
            List<SlaRoutingRuleParserService.AutoAssignRuleDefinition> matchedDefinitions = activeDefinitions.stream()
                    .filter(definition -> parserService.matchesDefinition(definition, candidate))
                    .toList();
            matchedDefinitions.forEach(definition -> usageStatsByRoute.get(definition.ruleId()).matchedTickets().add(parserService.ticketId(candidate)));
            List<SlaRoutingRuleParserService.AutoAssignRuleDefinition> winners = parserService.resolveWinningDefinitions(matchedDefinitions);
            if (!winners.isEmpty()) {
                winners.forEach(definition -> usageStatsByRoute.get(definition.ruleId()).selectedTickets().add(parserService.ticketId(candidate)));
            }
            if (winners.size() > 1) {
                Set<String> winnerRouteIds = winners.stream()
                        .map(SlaRoutingRuleParserService.AutoAssignRuleDefinition::ruleId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                for (SlaRoutingRuleParserService.AutoAssignRuleDefinition winner : winners) {
                    conflictTicketsByRoute.computeIfAbsent(winner.ruleId(), key -> new LinkedHashSet<>()).add(parserService.ticketId(candidate));
                    tiedRoutesByRoute.computeIfAbsent(winner.ruleId(), key -> new LinkedHashSet<>()).addAll(
                            winnerRouteIds.stream().filter(routeId -> !winner.ruleId().equals(routeId)).toList());
                }
            }
        }

        for (SlaRoutingRuleParserService.AutoAssignRuleDefinition definition : definitions) {
            RuleUsageStats usageStats = usageStatsByRoute.getOrDefault(definition.ruleId(), new RuleUsageStats());
            int matchedCount = usageStats.matchedTickets().size();
            int selectedCount = usageStats.selectedTickets().size();
            double coverageRate = safeCandidates.isEmpty() ? 0d : (double) matchedCount / safeCandidates.size();
            boolean broadRule = !safeCandidates.isEmpty()
                    && coverageRate >= (broadCoveragePct / 100d)
                    && definition.rule().specificityScore() <= 2;
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
                    conflictTicketsByRoute.containsKey(definition.ruleId()),
                    conflictTicketsByRoute.getOrDefault(definition.ruleId(), Set.of()),
                    tiedRoutesByRoute.getOrDefault(definition.ruleId(), Set.of()),
                    parserService.formatRuleAssigneeTarget(definition.rule())
            );
            issues.addAll(evaluation.emittedIssues());
            rules.add(evaluation.rulePayload());
        }

        Map<String, Long> decisionsByLayer = rules.stream().collect(Collectors.groupingBy(
                row -> String.valueOf(row.get("layer")),
                LinkedHashMap::new,
                Collectors.summingLong(row -> toLong(row.get("selected_candidates")))
        ));
        Map<String, Long> decisionsByRoute = rules.stream().collect(Collectors.groupingBy(
                row -> String.valueOf(row.get("route")),
                LinkedHashMap::new,
                Collectors.summingLong(row -> toLong(row.get("selected_candidates")))
        ));
        long conflictingRulesCount = conflictTicketsByRoute.keySet().size();
        long conflictingTicketsCount = conflictTicketsByRoute.values().stream().flatMap(Set::stream).distinct().count();
        long mandatoryIssueTotal = issues.stream().filter(issue -> "rollout_blocker".equals(String.valueOf(issue.get("classification")))).count();
        long advisoryIssueTotal = Math.max(0L, issues.size() - mandatoryIssueTotal);
        long conflictIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("conflict")).count();
        long reviewIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("review") || String.valueOf(issue.get("type")).contains("decision")).count();
        long ownershipIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("owner") || String.valueOf(issue.get("type")).contains("layer")).count();

        return new RoutingAuditAnalysis(definitions.size(), issues, rules, layerCounts, decisionsByLayer, decisionsByRoute,
                conflictTicketsByRoute, conflictingRulesCount, conflictingTicketsCount, mandatoryIssueTotal,
                advisoryIssueTotal, conflictIssueTotal, reviewIssueTotal, ownershipIssueTotal);
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

    private long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0L;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record RuleUsageStats(Set<String> matchedTickets, Set<String> selectedTickets) {
        private RuleUsageStats() {
            this(new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }
}
