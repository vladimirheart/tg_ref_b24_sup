package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleBehaviorServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleBehaviorService service = new SlaRoutingRuleBehaviorService();

    @Test
    void matchesAndBuildsRouteForPoolRule() {
        SlaRoutingRuleTypes.AutoAssignRule rule = parserService.parseDefinitions(List.of(
                Map.of(
                        "match_channel", "telegram",
                        "match_category", "billing",
                        "match_request_prefix", "INC",
                        "assign_to_pool", List.of("duty_a", "duty_b"),
                        "assign_to_pool_strategy", "rr"
                )
        )).get(0).rule();

        boolean matched = service.matches(rule, "telegram", null, null, Set.of("billing"), null,
                null, null, null, null, "inc-77");

        assertTrue(matched);
        assertEquals("rule_pool:duty_a:round_robin", service.route(rule));
        assertEquals("duty_a, duty_b", service.formatAssigneeTarget(rule));
    }

    @Test
    void specificityAndEmptyRuleBehavePredictably() {
        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> definitions = parserService.parseDefinitions(List.of(
                Map.of("rule_id", "specific", "match_channel", "telegram", "match_location", "moscow", "assign_to", "duty_a"),
                Map.of("rule_id", "empty", "assign_to", "duty_b")
        ));

        assertEquals(2, service.specificityScore(definitions.get(0).rule()));
        assertFalse(service.isEmptyRule(definitions.get(0).rule()));
        assertTrue(definitions.stream().noneMatch(definition -> "empty".equals(definition.ruleId())));
    }
}
