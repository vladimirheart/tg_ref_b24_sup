package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceTelemetryAnalyticsServiceTest {

    @Test
    void computeWorkspaceTelemetryTotalsAggregatesRatesAndWeightedOpenAverage() {
        DialogWorkspaceTelemetryAnalyticsService service =
                new DialogWorkspaceTelemetryAnalyticsService(mock(SharedConfigService.class));

        Map<String, Object> totals = service.computeWorkspaceTelemetryTotals(List.of(
                row(
                        "events", 12L,
                        "workspace_open_events", 8L,
                        "render_errors", 1L,
                        "fallbacks", 1L,
                        "abandons", 0L,
                        "slow_open_events", 2L,
                        "avg_open_ms", 1000L,
                        "context_profile_gap_events", 2L,
                        "context_sources_expanded_events", 3L,
                        "context_attribute_policy_expanded_events", 1L,
                        "context_extra_attributes_expanded_events", 2L
                ),
                row(
                        "events", 8L,
                        "workspace_open_events", 4L,
                        "render_errors", 0L,
                        "fallbacks", 0L,
                        "abandons", 1L,
                        "slow_open_events", 0L,
                        "avg_open_ms", 500L,
                        "context_profile_gap_events", 0L,
                        "context_sources_expanded_events", 1L,
                        "context_attribute_policy_expanded_events", 0L,
                        "context_extra_attributes_expanded_events", 0L
                )
        ));

        assertThat(totals).containsEntry("events", 20L);
        assertThat(totals).containsEntry("workspace_open_events", 12L);
        assertThat(totals).containsEntry("avg_open_ms", 794L);
        assertThat(totals).containsEntry("context_profile_gap_events", 2L);
        assertThat(totals.get("context_profile_gap_rate")).isEqualTo(2d / 12d);
        assertThat(totals).containsEntry("context_secondary_details_expanded_events", 7L);
        assertThat(totals).containsEntry("context_secondary_details_usage_level", "heavy");
    }

    @Test
    void buildWorkspaceCohortComparisonChoosesTestWhenOpenTimeImprovesWithoutRegressions() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "workspace_rollout_required_primary_kpis", List.of("frt"),
                        "workspace_rollout_min_kpi_events", 1,
                        "workspace_rollout_min_kpi_coverage_rate", 0.01
                )
        ));
        DialogWorkspaceTelemetryAnalyticsService service =
                new DialogWorkspaceTelemetryAnalyticsService(sharedConfigService);

        Map<String, Object> comparison = service.buildWorkspaceCohortComparison(List.of(
                row(
                        "experiment_cohort", "control",
                        "events", 40L,
                        "workspace_open_events", 40L,
                        "avg_open_ms", 1000L,
                        "kpi_frt_events", 20L,
                        "kpi_frt_recorded_events", 20L,
                        "avg_frt_ms", 900L
                ),
                row(
                        "experiment_cohort", "test",
                        "events", 42L,
                        "workspace_open_events", 42L,
                        "avg_open_ms", 820L,
                        "kpi_frt_events", 22L,
                        "kpi_frt_recorded_events", 22L,
                        "avg_frt_ms", 700L
                )
        ), Map.of("cohort_min_events", 30));

        assertThat(comparison).containsEntry("sample_size_ok", true);
        assertThat(comparison).containsEntry("winner", "test");
        assertThat(comparison).containsEntry("avg_open_ms_delta", -180L);
        assertThat(((Map<?, ?>) comparison.get("kpi_signal")).get("ready_for_decision")).isEqualTo(true);
    }

    @Test
    void buildWorkspaceGuardrailsAddsThresholdAndRegressionAlerts() {
        DialogWorkspaceTelemetryAnalyticsService service =
                new DialogWorkspaceTelemetryAnalyticsService(mock(SharedConfigService.class));

        Map<String, Object> guardrails = service.buildWorkspaceGuardrails(
                row("events", 100L, "render_errors", 4L, "fallbacks", 1L, "abandons", 0L, "slow_open_events", 0L),
                row("events", 100L, "render_errors", 0L, "fallbacks", 0L, "abandons", 0L, "slow_open_events", 0L),
                List.of(row("experiment_cohort", "test", "events", 100L, "render_errors", 4L)),
                List.of(),
                List.of(),
                Map.of("guardrail_render_error_rate", 0.01)
        );

        assertThat(guardrails).containsEntry("status", "attention");
        assertThat(((Map<?, ?>) guardrails.get("rates")).get("threshold_render_error")).isEqualTo(0.01d);
        assertThat((List<Map<String, Object>>) guardrails.get("alerts"))
                .extracting(alert -> String.valueOf(alert.get("metric")))
                .contains("render_error");
    }

    @Test
    void buildWorkspaceCohortComparisonKeepsOutcomeSignalsReadyForRolloutLikeDataset() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of()));
        DialogWorkspaceTelemetryAnalyticsService service =
                new DialogWorkspaceTelemetryAnalyticsService(sharedConfigService);

        Map<String, Object> comparison = service.buildWorkspaceCohortComparison(List.of(
                row(
                        "experiment_cohort", "control",
                        "events", 59L,
                        "workspace_open_events", 35L,
                        "avg_open_ms", 1000L,
                        "kpi_frt_events", 24L,
                        "kpi_ttr_events", 24L,
                        "kpi_sla_breach_events", 24L,
                        "kpi_frt_recorded_events", 8L,
                        "kpi_ttr_recorded_events", 8L,
                        "kpi_sla_breach_recorded_events", 8L,
                        "avg_frt_ms", 1000L,
                        "avg_ttr_ms", 2000L
                ),
                row(
                        "experiment_cohort", "test",
                        "events", 59L,
                        "workspace_open_events", 35L,
                        "avg_open_ms", 900L,
                        "kpi_frt_events", 24L,
                        "kpi_ttr_events", 24L,
                        "kpi_sla_breach_events", 24L,
                        "kpi_frt_recorded_events", 8L,
                        "kpi_ttr_recorded_events", 8L,
                        "kpi_sla_breach_recorded_events", 8L,
                        "avg_frt_ms", 900L,
                        "avg_ttr_ms", 1900L
                )
        ), Map.of());

        Map<String, Object> kpiSignal = (Map<String, Object>) comparison.get("kpi_signal");
        Map<String, Object> outcomeSignal = (Map<String, Object>) comparison.get("kpi_outcome_signal");

        assertThat(comparison).containsEntry("sample_size_ok", true);
        assertThat(comparison).containsEntry("winner", "test");
        assertThat(kpiSignal).containsEntry("ready_for_decision", true);
        assertThat(outcomeSignal).containsEntry("ready_for_decision", true);
        assertThat(outcomeSignal).containsEntry("has_regression", false);
    }

    @Test
    void resolveWorkspaceTelemetryConfigReturnsEmptyMapWhenSettingsAreMissing() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(null);
        DialogWorkspaceTelemetryAnalyticsService service =
                new DialogWorkspaceTelemetryAnalyticsService(sharedConfigService);

        assertThat(service.resolveWorkspaceTelemetryConfig()).isEmpty();
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }
}
