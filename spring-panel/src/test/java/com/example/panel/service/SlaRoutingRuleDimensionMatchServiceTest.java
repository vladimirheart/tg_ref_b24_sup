package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleDimensionMatchServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleDimensionMatchService service = new SlaRoutingRuleDimensionMatchService();

    @Test
    void matchesHandlesCategoryModeAndExcludedCategories() {
        SlaRoutingRuleTypes.AutoAssignRule rule = parserService.parseDefinitions(List.of(
                Map.of(
                        "match_channel", "telegram",
                        "match_categories", List.of("billing", "vip"),
                        "match_categories_mode", "all",
                        "exclude_categories", List.of("blacklist"),
                        "assign_to", "duty_a"
                )
        )).get(0).rule();

        assertTrue(service.matches(rule, "telegram", null, null, Set.of("billing", "vip"), null));
        assertFalse(service.matches(rule, "telegram", null, null, Set.of("billing", "vip", "blacklist"), null));
    }
}
