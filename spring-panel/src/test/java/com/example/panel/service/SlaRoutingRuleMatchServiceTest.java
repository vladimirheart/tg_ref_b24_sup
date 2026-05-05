package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleMatchServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleMatchService service = new SlaRoutingRuleMatchService();

    @Test
    void matchesHandlesCategoryAndRequestPrefixFilters() {
        SlaRoutingRuleTypes.AutoAssignRule rule = parserService.parseDefinitions(List.of(
                Map.of(
                        "match_channel", "telegram",
                        "match_categories", List.of("billing", "vip"),
                        "match_categories_mode", "all",
                        "match_request_prefix", "INC",
                        "assign_to", "duty_a"
                )
        )).get(0).rule();

        assertTrue(service.matches(rule, "telegram", null, null, Set.of("billing", "vip"), null,
                null, null, null, null, "inc-42"));
        assertFalse(service.matches(rule, "telegram", null, null, Set.of("billing"), null,
                null, null, null, null, "inc-42"));
    }

    @Test
    void isEmptyRuleRecognizesRuleWithoutMatchers() {
        SlaRoutingRuleTypes.AutoAssignRule rule = new SlaRoutingRuleTypes.AutoAssignRule(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                SlaRoutingRuleTypes.CategoryMatchMode.ANY, null, null, null, null, null,
                null, null, Set.of(), Set.of(), Set.of(), 0, "duty_a", List.of(), null,
                SlaRoutingRuleTypes.PoolAssignStrategy.HASH_BY_TICKET
        );

        assertTrue(service.isEmptyRule(rule));
    }
}
