package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleThresholdMatchServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingRuleThresholdMatchService service = new SlaRoutingRuleThresholdMatchService();

    @Test
    void matchesHandlesUnreadRatingMinutesAndState() {
        SlaRoutingRuleTypes.AutoAssignRule rule = parserService.parseDefinitions(List.of(
                Map.of(
                        "match_unread_min", 2,
                        "match_rating_min", 4,
                        "match_minutes_left_lte", 30,
                        "match_sla_state", "at_risk",
                        "assign_to", "duty_a"
                )
        )).get(0).rule();

        assertTrue(service.matches(rule, 3, 5, 20L, "at_risk"));
        assertFalse(service.matches(rule, 1, 5, 20L, "at_risk"));
    }
}
