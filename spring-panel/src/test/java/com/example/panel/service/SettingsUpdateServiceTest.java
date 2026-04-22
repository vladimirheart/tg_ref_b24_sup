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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsUpdateServiceTest {

    private SharedConfigService sharedConfigService;
    private SettingsDialogConfigUpdateService settingsDialogConfigUpdateService;
    private SettingsTopLevelUpdateService settingsTopLevelUpdateService;
    private SettingsLocationsUpdateService settingsLocationsUpdateService;
    private SettingsUpdateService service;

    @BeforeEach
    void setUp() {
        sharedConfigService = mock(SharedConfigService.class);
        settingsDialogConfigUpdateService = mock(SettingsDialogConfigUpdateService.class);
        settingsTopLevelUpdateService = mock(SettingsTopLevelUpdateService.class);
        settingsLocationsUpdateService = mock(SettingsLocationsUpdateService.class);
        service = new SettingsUpdateService(
                sharedConfigService,
                settingsDialogConfigUpdateService,
                settingsTopLevelUpdateService,
                settingsLocationsUpdateService
        );
    }

    @Test
    void updateSettingsSavesWhenAnySubserviceModifiedPayloadAndReturnsWarnings() {
        Map<String, Object> existingSettings = new LinkedHashMap<>(Map.of("theme", "neo"));
        when(sharedConfigService.loadSettings()).thenReturn(existingSettings);
        when(settingsTopLevelUpdateService.applyTopLevelUpdates(anyMap(), anyMap())).thenReturn(true);
        when(settingsDialogConfigUpdateService.applyDialogConfigUpdates(anyMap(), anyMap(), any(), anyList()))
                .thenAnswer(invocation -> {
                    List<String> warnings = invocation.getArgument(3);
                    warnings.add("dialog warning");
                    return false;
                });
        when(settingsLocationsUpdateService.applyLocationsUpdate(anyMap())).thenReturn(false);

        Map<String, Object> result = service.updateSettings(Map.of("categories", List.of("vip")), mock(Authentication.class));

        assertEquals(true, result.get("success"));
        assertEquals(List.of("dialog warning"), result.get("warnings"));
        verify(sharedConfigService).saveSettings(existingSettings);
        verify(settingsLocationsUpdateService).applyLocationsUpdate(Map.of("categories", List.of("vip")));
    }

    @Test
    void updateSettingsSkipsSaveWhenNothingChangedButStillAppliesLocationsUpdate() {
        Map<String, Object> existingSettings = new LinkedHashMap<>(Map.of("theme", "neo"));
        when(sharedConfigService.loadSettings()).thenReturn(existingSettings);
        when(settingsTopLevelUpdateService.applyTopLevelUpdates(anyMap(), anyMap())).thenReturn(false);
        when(settingsDialogConfigUpdateService.applyDialogConfigUpdates(anyMap(), anyMap(), any(), anyList())).thenReturn(false);

        Map<String, Object> payload = Map.of("locations", Map.of("tree", Map.of()));
        Map<String, Object> result = service.updateSettings(payload, mock(Authentication.class));

        assertEquals(Map.of("success", true), result);
        verify(sharedConfigService, never()).saveSettings(anyMap());
        verify(settingsLocationsUpdateService).applyLocationsUpdate(payload);
    }

    @Test
    void updateSettingsReturnsFailurePayloadWhenSubserviceThrows() {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>());
        when(settingsTopLevelUpdateService.applyTopLevelUpdates(anyMap(), anyMap()))
                .thenThrow(new IllegalStateException("bad payload"));

        Map<String, Object> result = service.updateSettings(Map.of("categories", List.of("vip")), mock(Authentication.class));

        assertEquals(false, result.get("success"));
        assertEquals("bad payload", result.get("error"));
        verify(sharedConfigService, never()).saveSettings(anyMap());
        verify(settingsLocationsUpdateService, never()).applyLocationsUpdate(anyMap());
    }
}
