package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingGovernanceIssueFactoryServiceTest {

    private final SlaRoutingGovernanceIssueFactoryService service = new SlaRoutingGovernanceIssueFactoryService();

    @Test
    void buildsStableGovernanceIssuePayload() {
        Map<String, Object> issue = service.buildGovernanceIssue(
                "rollout_blocker",
                "hold",
                "review_missing",
                "rule_alpha",
                "Review missing",
                "reviewed_at=missing",
                List.of("T-1"),
                List.of("rule_beta")
        );

        assertEquals("rollout_blocker", issue.get("classification"));
        assertEquals("hold", issue.get("status"));
        assertEquals("review_missing", issue.get("type"));
        assertEquals("rule_alpha", issue.get("rule_id"));
        assertEquals(List.of("T-1"), issue.get("tickets"));
        assertEquals(List.of("rule_beta"), issue.get("related"));
    }
}
