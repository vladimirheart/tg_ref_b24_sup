package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleDefinitionMatchServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleDefinitionMatchService service = new SlaRoutingRuleDefinitionMatchService();

    @Test
    void matchesDefinitionUsesNormalizedCandidateContext() {
        SlaRoutingRuleTypes.AutoAssignRuleDefinition definition = parserService.parseDefinitions(java.util.List.of(
                Map.of("rule_id", "alpha", "match_channel", "telegram", "match_sla_state", "at_risk", "assign_to", "duty_a")
        )).get(0);

        assertTrue(service.matchesDefinition(definition, Map.of("channel", "Telegram", "sla_state", "warning")));
        assertFalse(service.matchesDefinition(definition, Map.of("channel", "VK", "sla_state", "warning")));
    }
}
