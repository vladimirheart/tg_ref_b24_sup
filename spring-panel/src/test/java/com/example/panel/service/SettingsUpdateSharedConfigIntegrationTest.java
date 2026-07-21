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
                new BotSettingsPayloadNormalizer(),
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
                ),
                "statuses", Map.of(),
                "city_meta", Map.of(),
                "location_meta", Map.of()
        ));
    }

    @Test
    void updateSettingsSanitizesLocationsRuntimeStateBeforeSavingSharedConfig() {
        when(settingsDialogConfigUpdateService.applyDialogConfigUpdates(anyMap(), anyMap(), any(Authentication.class), anyList()))
                .thenReturn(false);

        Map<String, Object> result = service.updateSettings(Map.of(
                "locations", Map.of(
                        "tree", Map.of(
                                "Retail", Map.of("shop", Map.of("Moscow", List.of("Store 1")))
                        ),
                        "statuses", Map.of("location::Retail::shop::Moscow::Store 1", "Активен"),
                        "city_meta", Map.of("Retail::shop::Moscow", Map.of("country", "Россия", "partner_type", "shop")),
                        "location_meta", Map.of("Retail::shop::Moscow::Store 1", Map.of("country", "Россия", "partner_type", "shop")),
                        "locationsLoaded", false,
                        "locationsLoadingPromise", "should-not-be-saved"
                )
        ), mock(Authentication.class));

        assertEquals(Map.of("success", true), result);
        JsonNode locations = sharedConfigService.loadLocations();
        assertEquals("Store 1", locations.path("tree").path("Retail").path("shop").path("Moscow").get(0).asText());
        assertEquals("Активен", locations.path("statuses").path("location::Retail::shop::Moscow::Store 1").asText());
        assertEquals("Россия", locations.path("city_meta").path("Retail::shop::Moscow").path("country").asText());
        assertEquals("shop", locations.path("location_meta").path("Retail::shop::Moscow::Store 1").path("partner_type").asText());
        assertTrue(locations.path("locationsLoaded").isMissingNode());
        assertTrue(locations.path("locationsLoadingPromise").isMissingNode());
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
        assertTrue(!botSettings.containsKey("question_flow"));
    }

    @Test
    void updateSettingsPersistsCanonicalRatingTemplatesWithoutDerivedMirror() {
        when(settingsDialogConfigUpdateService.applyDialogConfigUpdates(anyMap(), anyMap(), any(Authentication.class), anyList()))
                .thenReturn(false);

        Map<String, Object> payload = Map.of(
                "bot_settings", Map.of(
                        "rating_templates", List.of(
                                Map.of(
                                        "id", "rating-template-default",
                                        "name", "Базовый сценарий оценок",
                                        "prompt_text", "Пожалуйста, оцените качество ответа от 1 до 5.",
                                        "scale_size", 5,
                                        "responses", List.of(
                                                Map.of("value", 1, "text", "Спасибо за вашу оценку 1! Нам важно ваше мнение."),
                                                Map.of("value", 5, "text", "Красавчик! Спасибо за вашу оценку 5! Нам важно ваше мнение.")
                                        )
                                )
                        ),
                        "active_rating_template_id", "rating-template-default"
                )
        );

        Map<String, Object> result = service.updateSettings(payload, mock(Authentication.class));

        assertEquals(Map.of("success", true), result);
        @SuppressWarnings("unchecked")
        Map<String, Object> botSettings = (Map<String, Object>) sharedConfigService.loadSettings().get("bot_settings");
        assertEquals("rating-template-default", botSettings.get("active_rating_template_id"));
        assertTrue(!botSettings.containsKey("rating_system"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ratingTemplates = (List<Map<String, Object>>) botSettings.get("rating_templates");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responses = (List<Map<String, Object>>) ratingTemplates.get(0).get("responses");
        assertEquals("Красавчик! Спасибо за вашу оценку 5! Нам важно ваше мнение.", responses.get(1).get("text"));
    }

    @Test
    void updateSettingsPersistsCanonicalAutoCloseConfigWithoutLegacyMirror() {
        when(settingsDialogConfigUpdateService.applyDialogConfigUpdates(anyMap(), anyMap(), any(Authentication.class), anyList()))
                .thenReturn(false);

        Map<String, Object> payload = Map.of(
                "auto_close_config", Map.of(
                        "templates", List.of(
                                Map.of("id", "auto-template-1", "hours", 1),
                                Map.of("id", "auto-template-2", "hours", 24)
                        ),
                        "active_template_id", "auto-template-1"
                )
        );

        Map<String, Object> result = service.updateSettings(payload, mock(Authentication.class));

        assertEquals(Map.of("success", true), result);
        assertEquals(
                Map.of(
                        "templates", List.of(
                                Map.of("id", "auto-template-1", "hours", 1),
                                Map.of("id", "auto-template-2", "hours", 24)
                        ),
                        "active_template_id", "auto-template-1"
                ),
                sharedConfigService.loadSettings().get("auto_close_config")
        );
        assertTrue(!sharedConfigService.loadSettings().containsKey("auto_close_hours"));
    }

    @Test
    void updateSettingsPersistsIikoServerSourcesAndSyncSettings() {
        when(settingsDialogConfigUpdateService.applyDialogConfigUpdates(anyMap(), anyMap(), any(Authentication.class), anyList()))
                .thenReturn(false);

        Map<String, Object> payload = Map.of(
                "locations_iiko_server_sources", List.of(
                        Map.of(
                                "id", "source-1",
                                "name", "Chain server",
                                "base_url", "https://chain.example/resto/",
                                "api_login", "login-1",
                                "api_secret", "0123456789abcdef0123456789abcdef01234567",
                                "enabled", true
                        )
                ),
                "locations_iiko_sync", Map.of(
                        "enabled", false,
                        "interval_minutes", 15
                )
        );

        Map<String, Object> result = service.updateSettings(payload, mock(Authentication.class));

        assertEquals(Map.of("success", true), result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = (List<Map<String, Object>>) sharedConfigService.loadSettings()
                .get(LocationsIikoServerSourceSettingsService.SETTINGS_KEY);
        assertEquals(1, sources.size());
        assertEquals("https://chain.example", sources.get(0).get("base_url"));
        assertEquals("login-1", sources.get(0).get("api_login"));
        assertEquals("0123456789abcdef0123456789abcdef01234567", sources.get(0).get("api_secret"));
        assertEquals(true, sources.get(0).get("enabled"));
        assertEquals(
                Map.of("enabled", false, "interval_minutes", 15),
                sharedConfigService.loadSettings().get(LocationsIikoSyncSettingsService.SETTINGS_KEY)
        );
    }
}
