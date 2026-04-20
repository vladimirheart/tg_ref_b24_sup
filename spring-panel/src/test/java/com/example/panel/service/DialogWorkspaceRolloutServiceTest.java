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
}
