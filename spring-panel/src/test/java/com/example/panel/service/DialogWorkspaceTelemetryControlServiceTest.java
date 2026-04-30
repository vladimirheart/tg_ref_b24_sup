package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceTelemetryControlServiceTest {

    private final DialogWorkspaceTelemetryControlService service = new DialogWorkspaceTelemetryControlService();

    @Test
    void buildSlaReviewPathControlUsesCheapPathConfirmedSignal() {
        Map<String, Object> payload = Map.of(
                "totals", Map.of("workspace_sla_policy_churn_level", "controlled")
        );
        Map<String, Object> audit = Map.of(
                "cheap_review_path_confirmed", true,
                "minimum_required_review_path_ready", true,
                "decision_lead_time_status", "cheap",
                "decision_lead_time_summary", "Decision lead time=4h (cheap)."
        );

        Map<String, Object> control = service.buildSlaReviewPathControl(payload, audit);

        assertThat(control)
                .containsEntry("status", "controlled")
                .containsEntry("cheap_review_path_confirmed", true)
                .containsEntry("minimum_required_review_path_ready", true)
                .containsEntry("decision_lead_time_status", "cheap");
        assertThat(String.valueOf(control.get("next_action_summary"))).contains("minimum required SLA review path");
    }

    @Test
    void buildWorkspaceWeeklyReviewFocusPromotesSlaSectionToTopPriority() {
        Map<String, Object> payload = Map.of(
                "totals", Map.of(
                        "workspace_sla_policy_churn_followup_required", true,
                        "workspace_sla_policy_churn_summary", "Policy churn increased",
                        "context_secondary_details_followup_required", true,
                        "context_secondary_details_summary", "Secondary noise"
                ),
                "previous_totals", Map.of(
                        "context_secondary_details_open_rate_pct", 10,
                        "context_extra_attributes_open_rate_pct", 5
                ),
                "rollout_packet", Map.of("legacy_only_inventory", Map.of())
        );
        Map<String, Object> slaAudit = Map.of(
                "weekly_review_followup_required", true,
                "policy_churn_risk_level", "high"
        );

        Map<String, Object> focus = service.buildWorkspaceWeeklyReviewFocus(payload, slaAudit, Map.of());

        assertThat(focus).containsEntry("status", "hold");
        assertThat(focus).containsEntry("top_priority_key", "sla");
        assertThat((List<?>) focus.get("sections")).hasSize(2);
    }
}
