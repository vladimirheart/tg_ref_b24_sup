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
                        new LocationsIikoServerSourceSettingsService(),
                        new LocationsIikoSyncSettingsService(),
                        mock(NotificationRoutingService.class)
                );

        assertFalse(service.applyTopLevelUpdates(Map.of("unknown_key", "value"), new LinkedHashMap<>()));
    }

    @Test
    void applyTopLevelUpdatesDerivesActiveBotFlowsAndRatingSystem() {
        SettingsTopLevelUpdateService service =
                new SettingsTopLevelUpdateService(
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
        assertEquals(
                List.of(Map.of("id", "q1", "type", "custom", "text", "Как вас зовут?")),
                botSettings.get("question_flow")
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> ratingSystem = (Map<String, Object>) botSettings.get("rating_system");
        assertEquals("Оцените диалог", ratingSystem.get("prompt_text"));
        assertEquals(5, ratingSystem.get("scale_size"));
        assertEquals(
                List.of(Map.of("value", 5, "text", "Красавчик! Спасибо за вашу оценку 5! Нам важно ваше мнение.")),
                ratingSystem.get("responses")
        );
    }
}
