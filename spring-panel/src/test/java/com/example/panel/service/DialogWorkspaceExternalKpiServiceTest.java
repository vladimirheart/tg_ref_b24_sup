package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceExternalKpiServiceTest {

    @Test
    void buildExternalKpiSignalMarksFreshReviewAsReadyWhenOnlyGateChecksAreEnabled() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        String reviewedAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).withNano(0).toString();
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "workspace_rollout_external_kpi_gate_enabled", true,
                        "workspace_rollout_external_kpi_omnichannel_ready", true,
                        "workspace_rollout_external_kpi_finance_ready", true,
                        "workspace_rollout_external_kpi_reviewed_by", "release-oncall",
                        "workspace_rollout_external_kpi_reviewed_at", reviewedAt,
                        "workspace_rollout_external_kpi_review_ttl_hours", 72
                )
        ));

        DialogWorkspaceExternalKpiService service = new DialogWorkspaceExternalKpiService(sharedConfigService);

        Map<String, Object> signal = service.buildExternalKpiSignal();

        assertThat(signal).containsEntry("ready_for_decision", true);
        assertThat(signal).containsEntry("review_present", true);
        assertThat(signal).containsEntry("review_fresh", true);
    }

    @Test
    void buildExternalKpiSignalNormalizesLegacyLocalReviewTimestampToUtc() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "workspace_rollout_external_kpi_gate_enabled", true,
                        "workspace_rollout_external_kpi_omnichannel_ready", true,
                        "workspace_rollout_external_kpi_finance_ready", true,
                        "workspace_rollout_external_kpi_reviewed_by", "release-oncall",
                        "workspace_rollout_external_kpi_reviewed_at", "2099-01-01T00:00",
                        "workspace_rollout_external_kpi_review_ttl_hours", 999999
                )
        ));

        DialogWorkspaceExternalKpiService service = new DialogWorkspaceExternalKpiService(sharedConfigService);

        Map<String, Object> signal = service.buildExternalKpiSignal();

        assertThat(signal).containsEntry("reviewed_at", "2099-01-01T00:00Z");
        assertThat(signal).containsEntry("review_present", true);
        assertThat(signal).containsEntry("ready_for_decision", true);
    }

    @Test
    void buildExternalKpiSignalReturnsDisabledGateWhenSettingsAreMissing() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(null);

        DialogWorkspaceExternalKpiService service = new DialogWorkspaceExternalKpiService(sharedConfigService);

        Map<String, Object> signal = service.buildExternalKpiSignal();

        assertThat(signal).containsEntry("enabled", false);
        assertThat(signal).containsEntry("ready_for_decision", true);
    }
}
