package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogWorkspaceTelemetrySummaryAssemblerServiceTest {

    @Test
    void assemblesDefaultWindowSummaryViaTelemetryBoundedServices() {
        DialogWorkspaceTelemetryDataService dataService = mock(DialogWorkspaceTelemetryDataService.class);
        DialogWorkspaceTelemetryAnalyticsService analyticsService = mock(DialogWorkspaceTelemetryAnalyticsService.class);
        DialogWorkspaceRolloutAssessmentService assessmentService = mock(DialogWorkspaceRolloutAssessmentService.class);
        DialogWorkspaceRolloutGovernanceService governanceService = mock(DialogWorkspaceRolloutGovernanceService.class);
        DialogWorkspaceTelemetrySummaryAssemblerService service = new DialogWorkspaceTelemetrySummaryAssemblerService(
                dataService,
                analyticsService,
                assessmentService,
                governanceService
        );

        when(analyticsService.resolveWorkspaceTelemetryConfig()).thenReturn(Map.of("workspace_experiment", "exp-a"));
        when(dataService.loadWorkspaceTelemetryRows(any(Instant.class), any(Instant.class), eq("exp-a"))).thenReturn(List.of());
        when(dataService.aggregateWorkspaceTelemetryRows(eq(List.of()), eq("shift"))).thenReturn(List.of());
        when(dataService.aggregateWorkspaceTelemetryRows(eq(List.of()), eq("team"))).thenReturn(List.of());
        when(analyticsService.computeWorkspaceTelemetryTotals(eq(List.of()))).thenReturn(Map.of("workspace_open_ms_count", 0));
        when(analyticsService.buildWorkspaceTelemetryComparison(any(), any())).thenReturn(Map.of("status", "stable"));
        when(analyticsService.buildWorkspaceCohortComparison(eq(List.of()), any())).thenReturn(Map.of("cohorts", List.of()));
        when(dataService.loadWorkspaceGapBreakdown(any(Instant.class), any(Instant.class), eq("exp-a"))).thenReturn(Map.of());
        when(analyticsService.buildWorkspaceGuardrails(any(), any(), eq(List.of()), eq(List.of()), eq(List.of()), any()))
                .thenReturn(Map.of("status", "ok"));
        when(assessmentService.buildRolloutDecision(any(), any())).thenReturn(Map.of("action", "monitor"));
        when(assessmentService.buildRolloutScorecard(any(), any(), any(), any())).thenReturn(Map.of("status", "ready"));
        when(governanceService.buildWorkspaceRolloutPacket(any(), any(), any(), any(), any(), eq(7), eq("exp-a")))
                .thenReturn(Map.of("status", "controlled"));

        Map<String, Object> payload = service.loadSummary(7, "exp-a");

        assertThat(payload).containsEntry("window_days", 7);
        assertThat(payload).containsEntry("rollout_decision", Map.of("action", "monitor"));
        assertThat(payload).containsEntry("rollout_packet", Map.of("status", "controlled"));
        verify(governanceService).buildWorkspaceRolloutPacket(any(), any(), any(), any(), any(), eq(7), eq("exp-a"));
    }
}
