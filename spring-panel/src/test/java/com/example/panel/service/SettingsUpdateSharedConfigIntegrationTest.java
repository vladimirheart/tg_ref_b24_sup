package com.example.panel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.Authentication;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettingsUpdateSharedConfigIntegrationTest {

    @TempDir
    Path tempDir;

    private SharedConfigService sharedConfigService;
    private SettingsDialogConfigUpdateService settingsDialogConfigUpdateService;
    private SettingsTopLevelUpdateService settingsTopLevelUpdateService;
    private SettingsParameterService settingsParameterService;
    private SettingsLocationsUpdateService settingsLocationsUpdateService;
    private SettingsUpdateService service;

    @BeforeEach
    void setUp() {
        sharedConfigService = new SharedConfigService(new ObjectMapper(), tempDir.toString());
        sharedConfigService.saveSettings(new java.util.LinkedHashMap<>());
        settingsDialogConfigUpdateService = mock(SettingsDialogConfigUpdateService.class);
        settingsTopLevelUpdateService = new SettingsTopLevelUpdateService(
                new LocationsIikoServerSourceSettingsService(),
                new LocationsIikoSyncSettingsService(),
                mock(NotificationRoutingService.class)
        );
        settingsParameterService = mock(SettingsParameterService.class);
        doNothing().when(settingsParameterService).syncParametersFromLocationsPayload(any());
        settingsLocationsUpdateService = new SettingsLocationsUpdateService(sharedConfigService, settingsParameterService);
        service = new SettingsUpdateService(
                sharedConfigService,
                settingsDialogConfigUpdateService,
                settingsTopLevelUpdateService,
                settingsLocationsUpdateService
        );
    }

    @Test
    void updateSettingsPersistsSettingsAndLocationsThroughSharedConfigBoundary() {
        when(settingsDialogConfigUpdateService.applyDialogConfigUpdates(anyMap(), anyMap(), any(Authentication.class), anyList()))
                .thenReturn(false);

        Map<String, Object> result = service.updateSettings(Map.of(
                "categories", List.of(" support ", "sales"),
                "reporting_config", Map.of("enabled", true),
                "locations", Map.of(
                        "tree", Map.of(
                                "Retail", Map.of("shop", Map.of("Moscow", List.of("Store 1")))
                        )
                )
        ), mock(Authentication.class));

        assertEquals(Map.of("success", true), result);
        assertEquals(List.of("support", "sales"), sharedConfigService.loadSettings().get("categories"));
        assertEquals(Map.of("enabled", true), sharedConfigService.loadSettings().get("reporting_config"));

        JsonNode locations = sharedConfigService.loadLocations();
        assertEquals("Store 1", locations.path("tree").path("Retail").path("shop").path("Moscow").get(0).asText());
        verify(settingsParameterService).syncParametersFromLocationsPayload(Map.of(
                "tree", Map.of(
                        "Retail", Map.of("shop", Map.of("Moscow", List.of("Store 1")))
                )
        ));
    }

    @Test
    void updateSettingsPersistsWarningsFromDialogConfigCoordinatorAlongsideTopLevelValues() {
        when(settingsDialogConfigUpdateService.applyDialogConfigUpdates(anyMap(), anyMap(), any(Authentication.class), anyList()))
                .thenAnswer(invocation -> {
                    Map<String, Object> settings = invocation.getArgument(1);
                    settings.put("dialog_config", Map.of("workspace_rollout_enabled", true));
                    @SuppressWarnings("unchecked")
                    List<String> warnings = invocation.getArgument(3);
                    warnings.add("dialog warning");
                    return true;
                });

        Map<String, Object> result = service.updateSettings(Map.of(
                "auto_close_hours", 72,
                "client_statuses", List.of("vip", "new")
        ), mock(Authentication.class));

        assertEquals(true, result.get("success"));
        assertEquals(List.of("dialog warning"), result.get("warnings"));
        assertEquals(72, sharedConfigService.loadSettings().get("auto_close_hours"));
        assertEquals(List.of("vip", "new"), sharedConfigService.loadSettings().get("client_statuses"));
        assertTrue(sharedConfigService.loadSettings().containsKey("dialog_config"));
    }

    @Test
    void updateSettingsPersistsBotQuestionTemplatesWithRequiredFlags() {
        when(settingsDialogConfigUpdateService.applyDialogConfigUpdates(anyMap(), anyMap(), any(Authentication.class), anyList()))
                .thenReturn(false);

        Map<String, Object> payload = Map.of(
                "bot_settings", Map.of(
                        "question_templates", List.of(
                                Map.of(
                                        "id", "template-1",
                                        "name", "Template 1",
                                        "question_flow", List.of(
                                                Map.of(
                                                        "id", "question-1",
                                                        "type", "custom",
                                                        "text", "Optional question",
                                                        "required", false
                                                )
                                        )
                                )
                        ),
                        "active_template_id", "template-1"
                )
        );

        Map<String, Object> result = service.updateSettings(payload, mock(Authentication.class));

        assertEquals(Map.of("success", true), result);
        @SuppressWarnings("unchecked")
        Map<String, Object> botSettings = (Map<String, Object>) sharedConfigService.loadSettings().get("bot_settings");
        assertEquals("template-1", botSettings.get("active_template_id"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> templates = (List<Map<String, Object>>) botSettings.get("question_templates");
        assertEquals(1, templates.size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questionFlow = (List<Map<String, Object>>) templates.get(0).get("question_flow");
        assertEquals(1, questionFlow.size());
        assertEquals(false, questionFlow.get(0).get("required"));
        assertEquals("Optional question", questionFlow.get(0).get("text"));
    }
}
