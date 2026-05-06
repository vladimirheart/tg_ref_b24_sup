package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingRuleWinnerSelectionServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleWinnerSelectionService service = new SlaRoutingRuleWinnerSelectionService();

    @Test
    void resolveWinningDefinitionsPrefersSpecificityThenPriority() {
        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> definitions = parserService.parseDefinitions(List.of(
                Map.of("rule_id", "generic", "match_channel", "telegram", "assign_to", "duty_a", "priority", 1),
                Map.of("rule_id", "specific_low", "match_channel", "telegram", "match_location", "moscow", "assign_to", "duty_b", "priority", 1),
                Map.of("rule_id", "specific_high", "match_channel", "telegram", "match_location", "moscow", "assign_to", "duty_c", "priority", 5)
        ));

        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> winners = service.resolveWinningDefinitions(definitions);

        assertEquals(1, winners.size());
        assertEquals("specific_high", winners.get(0).ruleId());
    }
}
