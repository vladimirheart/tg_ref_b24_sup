package com.example.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsDialogConfigUpdateServiceTest {

    private SettingsDialogTemplateConfigService templateConfigService;
    private SettingsDialogRuntimeConfigService runtimeConfigService;
    private SettingsDialogSlaAiConfigService slaAiConfigService;
    private SettingsDialogWorkspaceConfigService workspaceConfigService;
    private SettingsDialogPublicFormConfigService publicFormConfigService;
    private SettingsDialogConfigRoutingService routingService;
    private SettingsDialogConfigSupportService supportService;
    private SettingsDialogConfigUpdateService service;

    @BeforeEach
    void setUp() {
        templateConfigService = mock(SettingsDialogTemplateConfigService.class);
        runtimeConfigService = mock(SettingsDialogRuntimeConfigService.class);
        slaAiConfigService = mock(SettingsDialogSlaAiConfigService.class);
        workspaceConfigService = mock(SettingsDialogWorkspaceConfigService.class);
        publicFormConfigService = mock(SettingsDialogPublicFormConfigService.class);
        routingService = mock(SettingsDialogConfigRoutingService.class);
        supportService = mock(SettingsDialogConfigSupportService.class);
        service = new SettingsDialogConfigUpdateService(
                templateConfigService,
                runtimeConfigService,
                slaAiConfigService,
                workspaceConfigService,
                publicFormConfigService,
                routingService,
                supportService
        );
    }

    @Test
    void applyDialogConfigUpdatesDelegatesToAllSubdomainsAndStoresDialogConfig() {
        when(routingService.hasDialogConfigUpdates(anyMap())).thenReturn(true);
        doNothing().when(templateConfigService).applySettings(anyMap(), anyMap(), any(), anyList());
        doNothing().when(runtimeConfigService).applySettings(anyMap(), anyMap());
        doNothing().when(slaAiConfigService).applySettings(anyMap(), anyMap());
        doNothing().when(workspaceConfigService).applySettings(anyMap(), anyMap(), anyList());
        doNothing().when(publicFormConfigService).applySettings(anyMap(), anyMap());
        doNothing().when(supportService).validateExternalKpiDatamartContract(anyMap());

        Map<String, Object> payload = Map.of("dialog_workspace_rollout_enabled", true);
        Map<String, Object> settings = new LinkedHashMap<>(Map.of("dialog_config", Map.of("existing_key", "existing_value")));
        List<String> warnings = new ArrayList<>();

        boolean modified = service.applyDialogConfigUpdates(payload, settings, mock(Authentication.class), warnings);

        assertTrue(modified);
        @SuppressWarnings("unchecked")
        Map<String, Object> dialogConfig = (Map<String, Object>) settings.get("dialog_config");
        assertEquals("existing_value", dialogConfig.get("existing_key"));
        verify(templateConfigService).applySettings(anyMap(), anyMap(), any(), anyList());
        verify(runtimeConfigService).applySettings(anyMap(), anyMap());
        verify(slaAiConfigService).applySettings(anyMap(), anyMap());
        verify(workspaceConfigService).applySettings(anyMap(), anyMap(), anyList());
        verify(publicFormConfigService).applySettings(anyMap(), anyMap());
        verify(supportService).validateExternalKpiDatamartContract(anyMap());
    }

    @Test
    void applyDialogConfigUpdatesReturnsFalseWhenRoutingSaysNoRelevantKeys() {
        when(routingService.hasDialogConfigUpdates(anyMap())).thenReturn(false);

        boolean modified = service.applyDialogConfigUpdates(
                Map.of("categories", List.of("vip")),
                new LinkedHashMap<>(),
                mock(Authentication.class),
                new ArrayList<>()
        );

        assertFalse(modified);
        verify(templateConfigService, never()).applySettings(anyMap(), anyMap(), any(), anyList());
        verify(supportService, never()).validateExternalKpiDatamartContract(anyMap());
    }
}
