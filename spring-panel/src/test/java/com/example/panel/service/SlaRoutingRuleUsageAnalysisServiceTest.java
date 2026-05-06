package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleUsageAnalysisServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleUsageAnalysisService service = new SlaRoutingRuleUsageAnalysisService();

    @Test
    void analyzeBuildsUsageStatsAndConflictMaps() {
        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> definitions = parserService.parseDefinitions(List.of(
                Map.of("rule_id", "alpha", "match_channel", "telegram", "assign_to", "duty_a"),
                Map.of("rule_id", "beta", "match_channel", "telegram", "assign_to", "duty_b")
        ));

        SlaRoutingRuleUsageAnalysisService.RuleUsageAnalysis analysis = service.analyze(
                List.of(
                        Map.of("ticket_id", "T-1", "channel", "telegram"),
                        Map.of("ticket_id", "T-2", "channel", "telegram")
                ),
                definitions
        );

        assertEquals(2, analysis.layerCounts().get("legacy"));
        assertEquals(2, analysis.usageStatsByRoute().get("alpha").matchedTickets().size());
        assertTrue(analysis.conflictTicketsByRoute().containsKey("alpha"));
        assertTrue(analysis.tiedRoutesByRoute().get("alpha").contains("beta"));
    }
}
