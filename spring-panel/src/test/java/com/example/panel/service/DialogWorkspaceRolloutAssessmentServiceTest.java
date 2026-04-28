package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceRolloutAssessmentServiceTest {

    @Test
    void buildRolloutDecisionReturnsScaleUpWhenSignalsAreReady() {
        DialogWorkspaceExternalKpiService externalKpiService = mock(DialogWorkspaceExternalKpiService.class);
        when(externalKpiService.buildExternalKpiSignal()).thenReturn(Map.of(
                "enabled", true,
                "ready_for_decision", true,
                "omnichannel_ready", true,
                "finance_ready", true
        ));
        DialogWorkspaceRolloutAssessmentService service = new DialogWorkspaceRolloutAssessmentService(externalKpiService);

        Map<String, Object> decision = service.buildRolloutDecision(
                Map.of(
                        "winner", "test",
                        "sample_size_ok", true,
                        "kpi_signal", Map.of("ready_for_decision", true),
                        "kpi_outcome_signal", Map.of("ready_for_decision", true, "has_regression", false)
                ),
                Map.of("status", "ok")
        );

        assertThat(decision).containsEntry("action", "scale_up");
        assertThat(decision).containsEntry("guardrails_status", "ok");
        assertThat(decision).containsEntry("kpi_signal_ready", true);
        assertThat(decision).containsEntry("kpi_outcome_ready", true);
    }

    @Test
    void buildRolloutScorecardCarriesUtcExternalCheckpointTimestamp() {
        DialogWorkspaceExternalKpiService externalKpiService = mock(DialogWorkspaceExternalKpiService.class);
        DialogWorkspaceRolloutAssessmentService service = new DialogWorkspaceRolloutAssessmentService(externalKpiService);
        String reviewedAtUtc = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0).toString();

        Map<String, Object> scorecard = service.buildRolloutScorecard(
                Map.of(
                        "context_profile_ready_rate", 0.98d,
                        "context_source_ready_rate", 0.97d,
                        "context_attribute_policy_ready_rate", 0.96d,
                        "context_block_ready_rate", 0.95d,
                        "workspace_parity_ready_rate", 0.97d,
                        "workspace_parity_gap_events", 1L,
                        "workspace_open_events", 20L
                ),
                Map.of(
                        "sample_size_ok", true,
                        "sample_size_min_events", 10L,
                        "control_events", 20L,
                        "test_events", 22L,
                        "kpi_signal", Map.of(
                                "ready_for_decision", true,
                                "min_events_per_cohort", 10L,
                                "min_coverage_rate_per_cohort", 0.05d,
                                "required_kpis", List.of("frt", "ttr", "sla_breach")
                        ),
                        "kpi_outcome_signal", Map.of(
                                "metrics", Map.of(
                                        "frt", Map.of(
                                                "ready", true,
                                                "regression", false,
                                                "type", "latency_ms",
                                                "control_value", 1000L,
                                                "test_value", 900L,
                                                "max_relative_regression", 0.10d
                                        )
                                )
                        )
                ),
                Map.of("status", "ok", "alerts", List.of()),
                Map.of(
                        "action", "scale_up",
                        "external_kpi_signal", Map.of(
                                "enabled", true,
                                "ready_for_decision", true,
                                "omnichannel_ready", true,
                                "finance_ready", true,
                                "reviewed_at", reviewedAtUtc,
                                "review_present", true,
                                "review_fresh", true,
                                "review_ttl_hours", 72L,
                                "reviewed_by", "release-oncall",
                                "data_freshness_required", false
                        )
                )
        );

        assertThat(scorecard).containsEntry("decision_action", "scale_up");
        assertThat((List<Map<String, Object>>) scorecard.get("items")).anySatisfy(item -> {
            if ("external_kpi_gate".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo(reviewedAtUtc);
            }
            if ("external_review".equals(item.get("key"))) {
                assertThat(item.get("status")).isEqualTo("ok");
                assertThat(item.get("measured_at")).isEqualTo(reviewedAtUtc);
            }
        });
    }
}
