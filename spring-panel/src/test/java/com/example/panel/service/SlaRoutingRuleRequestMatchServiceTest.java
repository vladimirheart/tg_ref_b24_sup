package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleRequestMatchServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleRequestMatchService service = new SlaRoutingRuleRequestMatchService();

    @Test
    void matchesHandlesIncludeAndExcludePrefixes() {
        SlaRoutingRuleTypes.AutoAssignRule rule = parserService.parseDefinitions(List.of(
                Map.of(
                        "match_request_prefixes", List.of("inc", "vip"),
                        "exclude_request_prefix", "test",
                        "assign_to", "duty_a"
                )
        )).get(0).rule();

        assertTrue(service.matches(rule, "INC-1"));
        assertFalse(service.matches(rule, "TEST-1"));
    }
}
