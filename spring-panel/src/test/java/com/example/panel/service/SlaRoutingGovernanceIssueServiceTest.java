package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernanceIssueServiceTest {

    private final SlaRoutingRuleParserService parserService = new SlaRoutingRuleParserService();
    private final SlaRoutingGovernanceIssueService service = new SlaRoutingGovernanceIssueService();

    @Test
    void evaluateRuleBuildsConflictAndBroadIssues() {
        SlaRoutingRuleParserService.AutoAssignRuleDefinition definition = parserService.parseDefinitions(List.of(
                Map.of("rule_id", "alpha", "match_channel", "telegram", "assign_to", "duty_a")
        )).get(0);

        SlaRoutingGovernanceIssueService.RuleGovernanceEvaluation evaluation = service.evaluateRule(
                definition,
                5,
                3,
                0.75d,
                true,
                false,
                false,
                false,
                false,
                168,
                Instant.now(),
                true,
                Set.of("T-1", "T-2"),
                Set.of("beta"),
                "duty_a"
        );

        assertEquals("attention", evaluation.rulePayload().get("status"));
        assertTrue(evaluation.emittedIssues().stream().anyMatch(issue -> "rule_conflict".equals(issue.get("type"))));
        assertTrue(evaluation.emittedIssues().stream().anyMatch(issue -> "broad_rule".equals(issue.get("type"))));
    }

    @Test
    void evaluateRuleBuildsOwnershipAndReviewIssues() {
        SlaRoutingRuleParserService.AutoAssignRuleDefinition definition = parserService.parseDefinitions(List.of(
                Map.of(
                        "rule_id", "legacy_rule",
                        "match_channel", "telegram",
                        "assign_to", "duty_a",
                        "layer", "legacy",
                        "reviewed_at", "broken-date"
                )
        )).get(0);

        SlaRoutingGovernanceIssueService.RuleGovernanceEvaluation evaluation = service.evaluateRule(
                definition,
                1,
                1,
                0.2d,
                false,
                true,
                true,
                true,
                true,
                24,
                Instant.now(),
                false,
                Set.of(),
                Set.of(),
                "duty_a"
        );

        assertEquals("hold", evaluation.rulePayload().get("status"));
        assertTrue(evaluation.emittedIssues().stream().anyMatch(issue -> "layer_missing".equals(issue.get("type"))));
        assertTrue(evaluation.emittedIssues().stream().anyMatch(issue -> "owner_missing".equals(issue.get("type"))));
        assertTrue(evaluation.emittedIssues().stream().anyMatch(issue -> "review_invalid_utc".equals(issue.get("type"))));
    }
}
