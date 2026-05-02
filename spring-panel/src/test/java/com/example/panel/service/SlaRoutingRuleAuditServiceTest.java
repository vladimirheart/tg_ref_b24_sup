package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingRuleAuditServiceTest {

    private final SlaRoutingRuleAuditService service = new SlaRoutingRuleAuditService();

    @Test
    void analyzeFlagsConflictsAndBroadRules() {
        SlaRoutingRuleAuditService.RoutingAuditAnalysis analysis = service.analyze(
                List.of(
                        Map.of("ticket_id", "T-1", "channel", "telegram", "sla_state", "at_risk"),
                        Map.of("ticket_id", "T-2", "channel", "telegram", "sla_state", "at_risk")
                ),
                List.of(
                        Map.of("rule_id", "alpha", "match_channel", "telegram", "assign_to", "duty_a"),
                        Map.of("rule_id", "beta", "match_channel", "telegram", "assign_to", "duty_b")
                ),
                Instant.now(),
                50,
                false,
                false,
                false,
                false,
                168
        );

        assertEquals(2, analysis.rulesTotal());
        assertEquals(2L, analysis.conflictingRulesCount());
        assertEquals(2L, analysis.conflictingTicketsCount());
        assertTrue(analysis.issues().stream().anyMatch(issue -> "rule_conflict".equals(issue.get("type"))));
        assertTrue(analysis.issues().stream().anyMatch(issue -> "broad_rule".equals(issue.get("type"))));
    }

    @Test
    void analyzeFlagsMissingLayerOwnerAndInvalidReview() {
        SlaRoutingRuleAuditService.RoutingAuditAnalysis analysis = service.analyze(
                List.of(Map.of("ticket_id", "T-1", "channel", "telegram", "sla_state", "at_risk")),
                List.of(
                        Map.of(
                                "rule_id", "legacy_rule",
                                "match_channel", "telegram",
                                "assign_to", "duty_a",
                                "layer", "legacy",
                                "reviewed_at", "broken-date"
                        )
                ),
                Instant.now(),
                90,
                true,
                true,
                true,
                true,
                24
        );

        assertTrue(analysis.issues().stream().anyMatch(issue -> "layer_missing".equals(issue.get("type"))));
        assertTrue(analysis.issues().stream().anyMatch(issue -> "owner_missing".equals(issue.get("type"))));
        assertTrue(analysis.issues().stream().anyMatch(issue -> "review_invalid_utc".equals(issue.get("type"))));
        assertEquals(1, analysis.layerCounts().get("legacy"));
    }
}
