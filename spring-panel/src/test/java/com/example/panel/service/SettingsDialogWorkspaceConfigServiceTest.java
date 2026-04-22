package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsDialogWorkspaceConfigServiceTest {

    @Test
    void applySettingsCopiesWorkspaceKeysAndNormalizesUtcFieldsThroughSupportService() {
        SettingsDialogConfigSupportService supportService = mock(SettingsDialogConfigSupportService.class);
        SettingsDialogWorkspaceConfigService service = new SettingsDialogWorkspaceConfigService(supportService);

        when(supportService.normalizeUtcTimestampSetting(
                eq("2026-04-01T10:00:00+03:00"),
                eq("Owner sign-off timestamp"),
                any()))
                .thenReturn("2026-04-01T07:00:00Z");
        when(supportService.normalizeUtcTimestampSetting(
                eq("2026-04-02T10:00:00+03:00"),
                eq("Дата review внешних KPI"),
                any()))
                .thenReturn("2026-04-02T07:00:00Z");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dialog_workspace_v1", true);
        payload.put("dialog_workspace_client_external_links", List.of(Map.of("label", "CRM")));
        payload.put("dialog_workspace_rollout_governance_owner_signoff_at", "2026-04-01T10:00:00+03:00");
        payload.put("dialog_workspace_rollout_external_kpi_reviewed_at", "2026-04-02T10:00:00+03:00");

        Map<String, Object> dialogConfig = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        service.applySettings(payload, dialogConfig, warnings);

        assertEquals(true, dialogConfig.get("workspace_v1"));
        assertEquals(List.of(Map.of("label", "CRM")), dialogConfig.get("workspace_client_external_links"));
        assertEquals("2026-04-01T07:00:00Z", dialogConfig.get("workspace_rollout_governance_owner_signoff_at"));
        assertEquals("2026-04-02T07:00:00Z", dialogConfig.get("workspace_rollout_external_kpi_reviewed_at"));
    }
}
