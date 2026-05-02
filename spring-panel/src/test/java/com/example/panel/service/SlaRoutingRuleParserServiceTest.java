package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleParserServiceTest {

    private final SlaRoutingRuleParserService service = new SlaRoutingRuleParserService();

    @Test
    void parseDefinitionsNormalizesRuleMetadata() {
        List<SlaRoutingRuleParserService.AutoAssignRuleDefinition> definitions = service.parseDefinitions(List.of(
                Map.of(
                        "name", "telegram_hot",
                        "match_channel", "Telegram",
                        "match_categories", List.of("Billing", "VIP"),
                        "assign_to_pool", List.of("duty_a", "duty_b", "duty_a"),
                        "layer", "team",
                        "owner", "ops.lead",
                        "reviewed_at", "2026-05-01T10:00:00Z"
                )
        ));

        assertEquals(1, definitions.size());
        SlaRoutingRuleParserService.AutoAssignRuleDefinition definition = definitions.get(0);
        assertEquals("telegram_hot", definition.ruleId());
        assertEquals("domain", definition.layer());
        assertEquals("ops.lead", definition.owner());
        assertEquals(List.of("duty_a", "duty_b"), definition.rule().assigneePool());
        assertTrue(definition.rule().categories().contains("billing"));
        assertTrue(definition.rule().categories().contains("vip"));
    }

    @Test
    void resolveWinningDefinitionsPrefersSpecificityThenPriority() {
        List<SlaRoutingRuleParserService.AutoAssignRuleDefinition> definitions = service.parseDefinitions(List.of(
                Map.of("rule_id", "generic", "match_channel", "telegram", "assign_to", "duty_a", "priority", 1),
                Map.of("rule_id", "specific_low", "match_channel", "telegram", "match_location", "moscow", "assign_to", "duty_b", "priority", 1),
                Map.of("rule_id", "specific_high", "match_channel", "telegram", "match_location", "moscow", "assign_to", "duty_c", "priority", 5)
        ));

        List<SlaRoutingRuleParserService.AutoAssignRuleDefinition> winners = service.resolveWinningDefinitions(definitions);
        assertEquals(1, winners.size());
        assertEquals("specific_high", winners.get(0).ruleId());
    }
}
