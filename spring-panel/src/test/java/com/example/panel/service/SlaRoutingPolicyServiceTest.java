package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingPolicyServiceTest {

    private final SlaRoutingPolicyService service = new SlaRoutingPolicyService(
            new SlaEscalationCandidateService(),
            new SlaEscalationAutoAssignService(null),
            new SlaRoutingRuleAuditService()
    );

    @Test
    void buildRoutingPolicySnapshotUsesRulePreviewInMonitorMode() {
        Instant now = Instant.now();
        DialogListItem dialog = dialog("T-POLICY", now.minusSeconds(23 * 60 * 60 + 50 * 60).toString(), "open", null);
        Map<String, Object> settings = Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", 1440,
                        "sla_critical_minutes", 30,
                        "sla_critical_orchestration_mode", "monitor",
                        "sla_critical_auto_assign_enabled", true,
                        "sla_critical_auto_assign_to", "fallback_duty",
                        "sla_critical_auto_assign_rules", List.of(
                                Map.of("rule_id", "tg_hot", "match_channel", "telegram", "assign_to", "tg_duty")
                        )
                )
        );

        Map<String, Object> payload = service.buildRoutingPolicySnapshot(dialog, settings);
        assertEquals("ready", payload.get("status"));
        assertEquals("monitor", payload.get("action"));
        assertEquals("tg_hot", payload.get("route"));
        assertEquals("tg_duty", payload.get("recommended_assignee"));
    }

    @Test
    void buildRoutingPolicySnapshotReturnsInvalidUtcWhenCreatedAtBroken() {
        DialogListItem dialog = dialog("T-BROKEN", "not-a-date", "open", null);

        Map<String, Object> payload = service.buildRoutingPolicySnapshot(dialog, Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", 1440,
                        "sla_critical_minutes", 30
                )
        ));

        assertEquals("invalid_utc", payload.get("status"));
        assertEquals("attention", payload.get("action"));
        assertFalse((Boolean) payload.get("ready"));
    }

    @Test
    void buildRoutingGovernanceAuditFlagsAmbiguousAndBroadRules() {
        Instant now = Instant.now();

        Map<String, Object> audit = service.buildRoutingGovernanceAudit(
                List.of(
                        dialog("T-AUDIT-1", now.minusSeconds(23 * 60 * 60 + 50 * 60).toString(), "open", null),
                        dialog("T-AUDIT-2", now.minusSeconds(23 * 60 * 60 + 40 * 60).toString(), "open", null)
                ),
                Map.of(
                        "dialog_config", Map.of(
                                "sla_target_minutes", 1440,
                                "sla_critical_minutes", 30,
                                "sla_critical_auto_assign_enabled", true,
                                "sla_critical_auto_assign_audit_broad_rule_coverage_pct", 50,
                                "sla_critical_auto_assign_rules", List.of(
                                        Map.of("rule_id", "rule_alpha", "match_channel", "telegram", "assign_to", "alpha"),
                                        Map.of("rule_id", "rule_beta", "match_channel", "telegram", "assign_to", "beta")
                                )
                        )
                )
        );

        assertEquals("attention", audit.get("status"));
        List<Map<String, Object>> issues = (List<Map<String, Object>>) audit.get("issues");
        assertTrue(issues.stream().anyMatch(issue -> "rule_conflict".equals(issue.get("type"))));
        assertTrue(issues.stream().anyMatch(issue -> "broad_rule".equals(issue.get("type"))));
    }

    @Test
    void buildRoutingGovernanceAuditAppliesStrictReviewPathAndLeadTime() {
        Map<String, Object> audit = service.buildRoutingGovernanceAudit(
                List.of(dialog("T-AUDIT-STRICT", "2026-03-26T10:00:00Z", "open", null)),
                Map.of(
                        "dialog_config", Map.of(
                                "sla_target_minutes", 1440,
                                "sla_critical_minutes", 30,
                                "sla_critical_auto_assign_enabled", true,
                                "sla_critical_auto_assign_governance_review_path", "strict",
                                "sla_critical_auto_assign_governance_policy_changed_at", "2026-03-26T08:00:00Z",
                                "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                                "sla_critical_auto_assign_governance_reviewed_at", "2026-03-26T12:00:00Z",
                                "sla_critical_auto_assign_governance_decision", "go",
                                "sla_critical_auto_assign_governance_dry_run_ticket_id", "INC-42",
                                "sla_critical_auto_assign_rules", List.of(
                                        Map.of("rule_id", "rule_reviewed", "match_channel", "telegram", "assign_to", "duty")
                                )
                        )
                )
        );

        Map<String, Object> governanceReview = (Map<String, Object>) audit.get("governance_review");
        assertEquals("strict", governanceReview.get("review_path"));
        assertEquals(4L, governanceReview.get("decision_lead_time_hours"));
        assertEquals(true, governanceReview.get("decision_ready"));
        assertEquals(false, audit.get("minimum_required_review_path_ready"));
        assertEquals("close_required_path", audit.get("weekly_review_priority"));
    }

    private DialogListItem dialog(String ticketId, String createdAt, String status, String responsible) {
        return new DialogListItem(
                ticketId,
                100L,
                1L,
                "client",
                "Client",
                "biz",
                10L,
                "Telegram",
                "Moscow",
                "HQ",
                "Issue",
                createdAt,
                status,
                null,
                null,
                responsible,
                null,
                null,
                null,
                "user",
                createdAt,
                0,
                null,
                null
        );
    }
}
