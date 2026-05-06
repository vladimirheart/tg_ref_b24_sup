package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleEvaluationContextServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleUsageAnalysisService usageAnalysisService = new SlaRoutingRuleUsageAnalysisService();
    private final SlaRoutingRuleEvaluationContextService service = new SlaRoutingRuleEvaluationContextService();

    @Test
    void buildComputesCoverageConflictAndAssigneeTarget() {
        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> definitions = parserService.parseDefinitions(List.of(
                Map.of("rule_id", "alpha", "match_channel", "telegram", "assign_to", "duty_a"),
                Map.of("rule_id", "beta", "match_channel", "telegram", "assign_to_pool", List.of("duty_b", "duty_c"))
        ));
        SlaRoutingRuleUsageAnalysisService.RuleUsageAnalysis usage = usageAnalysisService.analyze(
                List.of(Map.of("ticket_id", "T-1", "channel", "telegram")),
                definitions
        );

        SlaRoutingRuleEvaluationContextService.RuleEvaluationContext context =
                service.build(definitions.get(0), usage, 1, 50);

        assertEquals(1, context.matchedCount());
        assertEquals(1, context.selectedCount());
        assertTrue(context.coverageRate() > 0d);
        assertTrue(context.hasConflict());
        assertEquals("duty_a", context.assigneeTarget());
    }
}
