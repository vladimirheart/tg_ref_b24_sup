package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingRuleDescriptorServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleDescriptorService service = new SlaRoutingRuleDescriptorService();

    @Test
    void routeAndSpecificityUsePoolFallbackWhenRuleIdMissing() {
        SlaRoutingRuleTypes.AutoAssignRule rule = parserService.parseDefinitions(List.of(
                Map.of(
                        "match_channel", "telegram",
                        "match_location", "moscow",
                        "assign_to_pool", List.of("duty_a", "duty_b"),
                        "assign_to_pool_strategy", "rr"
                )
        )).get(0).rule();

        assertEquals("rule_pool:duty_a:round_robin", service.route(rule));
        assertEquals("duty_a, duty_b", service.formatAssigneeTarget(rule));
        assertEquals(2, service.specificityScore(rule));
    }
}
