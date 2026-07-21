package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SettingsTopLevelUpdateServiceTest {

    @Test
    void applyTopLevelUpdatesNormalizesListsAndMaps() {
        SettingsTopLevelUpdateService service =
                new SettingsTopLevelUpdateService(
                        new BotSettingsPayloadNormalizer(),
                        new LocationsIikoServerSourceSettingsService(),
                        new LocationsIikoSyncSettingsService(),
                        mock(NotificationRoutingService.class)
                );
        Map<String, Object> settings = new LinkedHashMap<>();

        boolean modified = service.applyTopLevelUpdates(Map.of(
                "auto_close_hours", 48,
                "categories", List.of(" support ", "", "sales"),
                "client_statuses", List.of("vip", "vip", " new "),
                "client_status_colors", Map.of(" vip ", " #fff000 ", "", "ignored"),
                "reporting_config", Map.of("enabled", true)
        ), settings);

        assertTrue(modified);
        assertEquals(48, settings.get("auto_close_hours"));
        assertEquals(List.of("support", "sales"), settings.get("categories"));
        assertEquals(List.of("vip", "new"), settings.get("client_statuses"));
        assertEquals(Map.of("vip", "#fff000"), settings.get("client_status_colors"));
        assertEquals(Map.of("enabled", true), settings.get("reporting_config"));
    }

    @Test
    void applyTopLevelUpdatesReturnsFalseWhenPayloadDoesNotContainKnownKeys() {
        SettingsTopLevelUpdateService service =
                new SettingsTopLevelUpdateService(
                        new BotSettingsPayloadNormalizer(),
                        new LocationsIikoServerSourceSettingsService(),
                        new LocationsIikoSyncSettingsService(),
                        mock(NotificationRoutingService.class)
                );

        assertFalse(service.applyTopLevelUpdates(Map.of("unknown_key", "value"), new LinkedHashMap<>()));
    }

    @Test
    void applyTopLevelUpdatesPersistsCanonicalBotTemplatesWithoutDerivedMirrors() {
        SettingsTopLevelUpdateService service =
                new SettingsTopLevelUpdateService(
                        new BotSettingsPayloadNormalizer(),
                        new LocationsIikoServerSourceSettingsService(),
                        new LocationsIikoSyncSettingsService(),
                        mock(NotificationRoutingService.class)
                );
        Map<String, Object> settings = new LinkedHashMap<>();

        boolean modified = service.applyTopLevelUpdates(Map.of(
                "bot_settings", Map.of(
                        "question_templates", List.of(
                                Map.of(
                                        "id", "bot-template-1",
                                        "question_flow", List.of(Map.of("id", "q1", "type", "custom", "text", "Как вас зовут?"))
                                )
                        ),
                        "active_template_id", "bot-template-1",
                        "rating_templates", List.of(
                                Map.of(
                                        "id", "rating-template-1",
                                        "prompt_text", "Оцените диалог",
                                        "scale_size", 5,
                                        "responses", List.of(
                                                Map.of("value", 5, "text", "Красавчик! Спасибо за вашу оценку 5! Нам важно ваше мнение.")
                                        )
                                )
                        ),
                        "active_rating_template_id", "rating-template-1"
                )
        ), settings);

        assertTrue(modified);
        @SuppressWarnings("unchecked")
        Map<String, Object> botSettings = (Map<String, Object>) settings.get("bot_settings");
        assertEquals("bot-template-1", botSettings.get("active_template_id"));
        assertEquals("rating-template-1", botSettings.get("active_rating_template_id"));
        assertFalse(botSettings.containsKey("question_flow"));
        assertFalse(botSettings.containsKey("rating_system"));
    }

    @Test
    void applyTopLevelUpdatesPersistsCanonicalAutoCloseConfigWithoutLegacyMirror() {
        SettingsTopLevelUpdateService service =
                new SettingsTopLevelUpdateService(
                        new BotSettingsPayloadNormalizer(),
                        new LocationsIikoServerSourceSettingsService(),
                        new LocationsIikoSyncSettingsService(),
                        mock(NotificationRoutingService.class)
                );
        Map<String, Object> settings = new LinkedHashMap<>();

        boolean modified = service.applyTopLevelUpdates(Map.of(
                "auto_close_config", Map.of(
                        "templates", List.of(
                                Map.of("id", "auto-1", "hours", 1),
                                Map.of("id", "auto-2", "hours", 72)
                        ),
                        "active_template_id", "auto-1"
                ),
                "auto_close_hours", 48
        ), settings);

        assertTrue(modified);
        assertFalse(settings.containsKey("auto_close_hours"));
        assertEquals(
                Map.of(
                        "templates", List.of(
                                Map.of("id", "auto-1", "hours", 1),
                                Map.of("id", "auto-2", "hours", 72)
                        ),
                        "active_template_id", "auto-1"
                ),
                settings.get("auto_close_config")
        );
    }

    @Test
    void applyTopLevelUpdatesImportsLegacyBotSettingsIntoCanonicalTemplates() {
        SettingsTopLevelUpdateService service =
                new SettingsTopLevelUpdateService(
                        new BotSettingsPayloadNormalizer(),
                        new LocationsIikoServerSourceSettingsService(),
                        new LocationsIikoSyncSettingsService(),
                        mock(NotificationRoutingService.class)
                );
        Map<String, Object> settings = new LinkedHashMap<>();

        boolean modified = service.applyTopLevelUpdates(Map.of(
                "bot_settings", Map.of(
                        "question_flow", List.of(
                                Map.of("id", "q-legacy", "type", "custom", "text", "Старый вопрос")
                        ),
                        "rating_system", Map.of(
                                "prompt_text", "Оцените старый сценарий",
                                "scale_size", 5,
                                "responses", List.of(
                                        Map.of("value", 5, "text", "Старый ответ 5")
                                )
                        )
                )
        ), settings);

        assertTrue(modified);
        @SuppressWarnings("unchecked")
        Map<String, Object> botSettings = (Map<String, Object>) settings.get("bot_settings");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questionTemplates = (List<Map<String, Object>>) botSettings.get("question_templates");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ratingTemplates = (List<Map<String, Object>>) botSettings.get("rating_templates");

        assertEquals("Импортированный сценарий", questionTemplates.get(0).get("name"));
        assertEquals("Импортированный шаблон оценок", ratingTemplates.get(0).get("name"));
        assertFalse(botSettings.containsKey("question_flow"));
        assertFalse(botSettings.containsKey("rating_system"));
    }
}
