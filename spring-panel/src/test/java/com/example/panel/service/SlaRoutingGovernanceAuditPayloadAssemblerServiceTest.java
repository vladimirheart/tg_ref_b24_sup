package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SlaRoutingGovernanceAuditPayloadAssemblerServiceTest {

    private final SlaRoutingGovernanceAuditPayloadAssemblerService service = new SlaRoutingGovernanceAuditPayloadAssemblerService();

    @Test
    void assembleProjectsCheckpointAndIssueBreakdownIntoStablePayload() {
        SlaRoutingRuleAuditService.RoutingAuditAnalysis analysis = new SlaRoutingRuleAuditService.RoutingAuditAnalysis(
                2,
                List.of(),
                List.of(Map.of("layer", "domain", "route", "rule_a", "selected_candidates", 1)),
                Map.of("domain", 1),
                Map.of("domain", 1L),
                Map.of("rule_a", 1L),
                Map.of(),
                1,
                0,
                0,
                0,
                0,
                0,
                0
        );
        SlaRoutingGovernanceCheckpointService.GovernanceCheckpointSummary checkpointSummary =
                new SlaRoutingGovernanceCheckpointService.GovernanceCheckpointSummary(
                        List.of("utc_review"),
                        Map.of("utc_review", true),
                        1,
                        1,
                        100,
                        1,
                        1,
                        100,
                        0,
                        "controlled",
                        "controlled",
                        "monitor",
                        "ok",
                        false,
                        false,
                        true,
                        "cheap",
                        "Decision lead time=1h (cheap).",
                        "controlled",
                        0,
                        "controlled",
                        true,
                        true,
                        "Required path: utc_review (ready, lead=cheap).",
                        List.of()
                );
        Map<String, Object> requirements = Map.of("required", true);
        Map<String, Object> governanceReview = Map.of("decision_ready", true);
        List<Map<String, Object>> issues = List.of(Map.of("type", "rule_conflict"));
        List<Map<String, Object>> rules = List.of(Map.of("route", "rule_a"));

        Map<String, Object> payload = service.assemble(
                "2026-05-05T10:00:00Z",
                "attention",
                "summary",
                true,
                true,
                "assist",
                false,
                2,
                analysis,
                1,
                0,
                1,
                0,
                0,
                checkpointSummary,
                requirements,
                governanceReview,
                issues,
                rules
        );

        assertEquals("attention", payload.get("status"));
        assertEquals(1L, ((Map<?, ?>) payload.get("issue_breakdown")).get("conflicts"));
        assertEquals(100L, payload.get("required_checkpoint_closure_rate_pct"));
        assertEquals("monitor", payload.get("weekly_review_priority"));
        assertSame(requirements, payload.get("requirements"));
        assertSame(governanceReview, payload.get("governance_review"));
        assertSame(issues, payload.get("issues"));
        assertSame(rules, payload.get("rules"));
    }
}
