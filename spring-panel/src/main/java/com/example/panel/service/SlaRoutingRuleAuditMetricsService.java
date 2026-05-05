package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlaRoutingRuleAuditMetricsService {

    public AuditMetrics summarize(List<Map<String, Object>> issues,
                                  List<Map<String, Object>> rules,
                                  Map<String, Set<String>> conflictTicketsByRoute) {
        List<Map<String, Object>> safeIssues = issues == null ? List.of() : issues;
        List<Map<String, Object>> safeRules = rules == null ? List.of() : rules;
        Map<String, Set<String>> safeConflicts = conflictTicketsByRoute == null ? Map.of() : conflictTicketsByRoute;

        Map<String, Long> decisionsByLayer = safeRules.stream().collect(Collectors.groupingBy(
                row -> String.valueOf(row.get("layer")),
                LinkedHashMap::new,
                Collectors.summingLong(row -> toLong(row.get("selected_candidates")))
        ));
        Map<String, Long> decisionsByRoute = safeRules.stream().collect(Collectors.groupingBy(
                row -> String.valueOf(row.get("route")),
                LinkedHashMap::new,
                Collectors.summingLong(row -> toLong(row.get("selected_candidates")))
        ));
        long conflictingRulesCount = safeConflicts.keySet().size();
        long conflictingTicketsCount = safeConflicts.values().stream().flatMap(Set::stream).distinct().count();
        long mandatoryIssueTotal = safeIssues.stream().filter(issue -> "rollout_blocker".equals(String.valueOf(issue.get("classification")))).count();
        long advisoryIssueTotal = Math.max(0L, safeIssues.size() - mandatoryIssueTotal);
        long conflictIssueTotal = safeIssues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("conflict")).count();
        long reviewIssueTotal = safeIssues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("review") || String.valueOf(issue.get("type")).contains("decision")).count();
        long ownershipIssueTotal = safeIssues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("owner") || String.valueOf(issue.get("type")).contains("layer")).count();

        return new AuditMetrics(decisionsByLayer, decisionsByRoute, conflictingRulesCount, conflictingTicketsCount,
                mandatoryIssueTotal, advisoryIssueTotal, conflictIssueTotal, reviewIssueTotal, ownershipIssueTotal);
    }

    public record AuditMetrics(Map<String, Long> decisionsByLayer,
                               Map<String, Long> decisionsByRoute,
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
}
