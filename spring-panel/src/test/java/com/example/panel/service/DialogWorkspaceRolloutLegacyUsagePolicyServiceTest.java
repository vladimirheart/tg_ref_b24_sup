package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceRolloutLegacyUsagePolicyServiceTest {

    @Test
    void buildLegacyUsagePolicyRequiresBlockedReasonsFollowupWhenConfigured() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        DialogWorkspaceTelemetryDataService telemetryDataService = mock(DialogWorkspaceTelemetryDataService.class);
        DialogWorkspaceTelemetryAnalyticsService telemetryAnalyticsService = mock(DialogWorkspaceTelemetryAnalyticsService.class);
        when(telemetryDataService.loadWorkspaceEventReasonBreakdown(anyString(), any(), any(), any(), anyInt()))
                .thenAnswer(inv -> {
                    String eventType = inv.getArgument(0, String.class);
                    if ("workspace_open_legacy_blocked".equals(eventType)) {
                        return List.of(
                                Map.of("reason", "policy_hold", "events", 2L),
                                Map.of("reason", "invalid_review_timestamp", "events", 1L)
                        );
                    }
                    return List.of();
                });
        when(telemetryDataService.loadWorkspaceTelemetryRows(any(), any(), any()))
                .thenReturn(List.of());
        when(telemetryAnalyticsService.computeWorkspaceTelemetryTotals(any()))
                .thenReturn(Map.of("workspace_open_events", 0L));

        DialogWorkspaceRolloutGovernanceConfigService configService =
                new DialogWorkspaceRolloutGovernanceConfigService(sharedConfigService);
        DialogWorkspaceRolloutLegacyUsagePolicyService service =
                new DialogWorkspaceRolloutLegacyUsagePolicyService(telemetryDataService, telemetryAnalyticsService, configService);

        DialogWorkspaceRolloutGovernanceConfig config = new DialogWorkspaceRolloutGovernanceConfig(
                true, false, "", "", 168,
                0, "", "", "", null, "",
                List.of(), List.of(), false, false, false, null, "",
                0, List.of(),
                List.of(), Map.of(),
                "", "", "",
                "ops-lead", Instant.now().toString(), "", null, 168,
                null, null, null, null,
                List.of(), false, true, 2, List.of("policy_hold"), "", false,
                false, List.of(), List.of(), Map.of(), List.of(), Map.of(),
                List.of(), Map.of(), Map.of(), "", "", "", 168
        );

        DialogWorkspaceRolloutSectionResult result = service.buildLegacyUsagePolicy(
                config,
                Map.of("workspace_open_events", 10L, "workspace_open_legacy_blocked_events", 3L, "manual_legacy_open_events", 0L),
                7,
                "exp-a"
        );

        assertThat(result.status()).isEqualTo("hold");
        assertThat(result.currentValue()).contains("blocked_review=1/2");
        assertThat((Map<String, Object>) result.payload()).containsEntry("blocked_reasons_review_ready", false);
        assertThat((List<String>) ((Map<String, Object>) result.payload()).get("blocked_reasons_missing"))
                .containsExactly("invalid_review_timestamp");
    }
}
