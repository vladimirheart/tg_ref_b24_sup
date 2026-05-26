package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceRolloutServiceTest {

    private final DialogWorkspaceRolloutService service = new DialogWorkspaceRolloutService();

    @Test
    void resolveRolloutMetaBuildsWorkspaceOnlyModePayload() {
        Map<String, Object> payload = service.resolveRolloutMeta(Map.of(
                "dialog_config", Map.of(
                        "workspace_v1", true,
                        "workspace_single_mode", true,
                        "workspace_ab_enabled", true
                )
        ));

        assertThat(payload.get("workspace_enabled")).isEqualTo(true);
        assertThat(payload.get("workspace_single_mode")).isEqualTo(true);
        assertThat(payload.get("mode")).isEqualTo("workspace_single_mode");
        assertThat(payload.get("banner_tone")).isEqualTo("success");
        assertThat(payload.get("legacy_fallback_available")).isEqualTo(false);
    }

    @Test
    void resolveRolloutMetaMarksLegacyManualOpenAsBlockedOnHoldDecision() {
        Map<String, Object> payload = service.resolveRolloutMeta(Map.of(
                "dialog_config", Map.of(
                        "workspace_v1", true,
                        "workspace_rollout_legacy_manual_open_policy_enabled", true,
                        "workspace_rollout_legacy_manual_open_block_on_hold", true,
                        "workspace_rollout_governance_legacy_usage_decision", "hold",
                        "workspace_rollout_governance_legacy_usage_reviewed_at", "2026-04-20T09:00:00Z",
                        "workspace_rollout_legacy_manual_open_allowed_reasons", List.of("sla_breach", "vip")
                )
        ));

        Map<?, ?> policy = (Map<?, ?>) payload.get("legacy_manual_open_policy");
        assertThat(policy.get("enabled")).isEqualTo(true);
        assertThat(policy.get("blocked")).isEqualTo(true);
        assertThat(policy.get("block_reason")).isEqualTo("review_decision_hold");
        assertThat(policy.get("allowed_reasons")).isEqualTo(List.of("sla_breach", "vip"));
    }

    @Test
    void resolveRolloutMetaBuildsCohortRolloutPayloadAndMarksStaleManualReview() {
        Map<String, Object> payload = service.resolveRolloutMeta(Map.of(
                "dialog_config", Map.ofEntries(
                        Map.entry("workspace_v1", true),
                        Map.entry("workspace_ab_enabled", true),
                        Map.entry("workspace_ab_rollout_percent", 35),
                        Map.entry("workspace_ab_experiment_name", "workspace-q2"),
                        Map.entry("workspace_ab_operator_segment", "night_shift"),
                        Map.entry("workspace_rollout_legacy_manual_open_policy_enabled", true),
                        Map.entry("workspace_rollout_legacy_manual_open_block_on_stale_review", true),
                        Map.entry("workspace_rollout_legacy_manual_open_review_ttl_hours", 24),
                        Map.entry("workspace_rollout_governance_legacy_usage_reviewed_by", "ops.lead"),
                        Map.entry("workspace_rollout_governance_legacy_usage_review_note", "Weekly review"),
                        Map.entry("workspace_rollout_governance_legacy_usage_reviewed_at", "2020-01-01T00:00:00Z"),
                        Map.entry("workspace_rollout_legacy_manual_open_reason_catalog_required", true),
                        Map.entry("workspace_rollout_legacy_manual_open_allowed_reasons", List.of("attachments_edit", "inline_reopen"))
                )
        ));

        Map<?, ?> policy = (Map<?, ?>) payload.get("legacy_manual_open_policy");
        assertThat(payload.get("mode")).isEqualTo("cohort_rollout");
        assertThat(payload.get("banner_tone")).isEqualTo("info");
        assertThat(payload.get("legacy_fallback_available")).isEqualTo(true);
        assertThat(payload.get("rollout_percent")).isEqualTo(35);
        assertThat(payload.get("experiment_name")).isEqualTo("workspace-q2");
        assertThat(payload.get("operator_segment")).isEqualTo("night_shift");
        assertThat(policy.get("enabled")).isEqualTo(true);
        assertThat(policy.get("blocked")).isEqualTo(true);
        assertThat(policy.get("block_reason")).isEqualTo("stale_review");
        assertThat(policy.get("reviewed_by")).isEqualTo("ops.lead");
        assertThat(policy.get("review_note")).isEqualTo("Weekly review");
        assertThat(policy.get("reason_catalog_required")).isEqualTo(true);
        assertThat(policy.get("allowed_reasons")).isEqualTo(List.of("attachments_edit", "inline_reopen"));
        assertThat(String.valueOf(policy.get("review_age_hours"))).isNotBlank();
        assertThat(payload.get("summary")).isEqualTo("Workspace включён в cohort-rollout; legacy modal остаётся fallback-механизмом.");
    }

    @Test
    void resolveRolloutMetaMarksLegacyManualOpenAsBlockedOnInvalidReviewTimestamp() {
        Map<String, Object> payload = service.resolveRolloutMeta(Map.of(
                "dialog_config", Map.of(
                        "workspace_v1", true,
                        "workspace_rollout_legacy_manual_open_policy_enabled", true,
                        "workspace_rollout_governance_legacy_usage_reviewed_at", "2026-05-26 10:15:00 MSK"
                )
        ));

        Map<?, ?> policy = (Map<?, ?>) payload.get("legacy_manual_open_policy");
        assertThat(policy.get("enabled")).isEqualTo(true);
        assertThat(policy.get("blocked")).isEqualTo(true);
        assertThat(policy.get("review_timestamp_invalid")).isEqualTo(true);
        assertThat(policy.get("block_reason")).isEqualTo("invalid_review_timestamp");
        assertThat(policy.get("review_age_hours")).isEqualTo("");
    }
}
