package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleAuditEvaluationServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleUsageAnalysisService usageAnalysisService = new SlaRoutingRuleUsageAnalysisService(parserService);
    private final SlaRoutingRuleAuditEvaluationService service = new SlaRoutingRuleAuditEvaluationService();

    @Test
    void evaluateProducesRulePayloadsAndIssues() {
        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> definitions = parserService.parseDefinitions(List.of(
                Map.of("rule_id", "alpha", "match_channel", "telegram", "assign_to", "duty_a"),
                Map.of("rule_id", "beta", "match_channel", "telegram", "assign_to", "duty_b")
        ));
        SlaRoutingRuleUsageAnalysisService.RuleUsageAnalysis usage = usageAnalysisService.analyze(
                List.of(Map.of("ticket_id", "T-1", "channel", "telegram")),
                definitions
        );

        SlaRoutingRuleAuditEvaluationService.RuleEvaluationBundle bundle = service.evaluate(
                definitions, usage, 1, 50, false, false, false, false, 168, Instant.now()
        );

        assertTrue(bundle.rules().size() == 2);
        assertTrue(bundle.issues().stream().anyMatch(issue -> "rule_conflict".equals(issue.get("type")) || "broad_rule".equals(issue.get("type"))));
    }
}
